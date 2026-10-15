package com.xsy.scm.mall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.finance.service.FinanceOrderPayableQueryService;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.order.domain.vo.MallOrderVO;
import com.xsy.scm.mall.order.service.MallOrderQueryService;
import com.xsy.scm.mall.order.service.MallPaymentService;
import com.xsy.scm.order.domain.entity.SalesOrderEntity;
import com.xsy.scm.order.service.SalesOrderService;
import com.xsy.scm.payment.constant.ScmPaymentMethodEnum;
import com.xsy.scm.payment.constant.ScmPaymentProviderEnum;
import com.xsy.scm.payment.constant.ScmPaymentSourceTypeEnum;
import com.xsy.scm.payment.domain.form.PaymentIntentCreateForm;
import com.xsy.scm.payment.service.PaymentIntentService;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 商城余额支付的边界：金额只认服务端算出的待付额，未确认 / 非余额结算 / 已付清都必须被挡在扣款之前。
 */
@DisplayName("商城余额支付的归属与金额权威")
class MallPaymentServiceTest {
    private static final Long ORDER_ID = 21L;

    private final MallOrderQueryService orderQueryService = mock(MallOrderQueryService.class);
    private final SalesOrderService salesOrderService = mock(SalesOrderService.class);
    private final FinanceOrderPayableQueryService payableQueryService = mock(FinanceOrderPayableQueryService.class);
    private final PaymentIntentService paymentIntentService = mock(PaymentIntentService.class);
    private final ScmIdempotencyService idempotencyService = mock(ScmIdempotencyService.class);
    private final MallPaymentService service = new MallPaymentService(orderQueryService, salesOrderService,
            payableQueryService, paymentIntentService, idempotencyService);
    private final RequestMallCustomer customer = customer();

    {
        when(idempotencyService.claim(any(), any(), any()))
                .thenReturn(new ScmIdempotencyService.Claim(null, false));
    }

    @Test
    void payChargesTheServerComputedDueAmountNotAnythingTheClientSent() {
        when(orderQueryService.requireOwnedOrder(customer, ORDER_ID)).thenReturn(order("CONFIRMED", "WALLET"));
        when(salesOrderService.lock(ORDER_ID)).thenReturn(order("CONFIRMED", "WALLET"));
        when(payableQueryService.payableOfConfirmedOrder(ORDER_ID)).thenReturn(new BigDecimal("60.0000"));
        when(payableQueryService.paidAmountOf(ORDER_ID)).thenReturn(new BigDecimal("10.0000"));
        when(orderQueryService.detail(customer, ORDER_ID)).thenReturn(new MallOrderVO());

        service.pay(customer, ORDER_ID, "key");

        ArgumentCaptor<PaymentIntentCreateForm> captor = ArgumentCaptor.forClass(PaymentIntentCreateForm.class);
        verify(paymentIntentService).createFromMall(captor.capture(), eq("key"));
        PaymentIntentCreateForm form = captor.getValue();
        // 应付 60、已付 10：本笔只能扣 50，且来源身份取正式订单而不是请求参数
        assertThat(form.getAmount()).isEqualByComparingTo("50.0000");
        assertThat(form.getSourceType()).isEqualTo(ScmPaymentSourceTypeEnum.SALES_ORDER.name());
        assertThat(form.getSourceId()).isEqualTo(ORDER_ID);
        assertThat(form.getCustomerId()).isEqualTo(9L);
        assertThat(form.getMethod()).isEqualTo(ScmPaymentMethodEnum.BALANCE.name());
        assertThat(form.getProvider()).isEqualTo(ScmPaymentProviderEnum.INTERNAL_BALANCE.name());
    }

    @Test
    void sameKeyReplaysTheFirstPaidResultAfterRecheckingOwnership() {
        var claim = new ScmIdempotencyService.Claim(null, true);
        MallOrderVO paid = new MallOrderVO();
        paid.setPaidAmount(new BigDecimal("50.0000"));
        when(orderQueryService.requireOwnedOrder(customer, ORDER_ID)).thenReturn(order("CONFIRMED", "WALLET"));
        when(idempotencyService.claim(any(), eq("key"), any())).thenReturn(claim);
        when(idempotencyService.replay(claim, MallOrderVO.class)).thenReturn(paid);

        assertThat(service.pay(customer, ORDER_ID, "key")).isSameAs(paid);
        verify(orderQueryService).requireOwnedOrder(customer, ORDER_ID);
        verify(salesOrderService, never()).lock(any());
        verify(paymentIntentService, never()).createFromMall(any(), any());
    }

    @Test
    void missingIdempotencyKeyIsRejectedBeforeAnyLockOrDeduction() {
        assertThatThrownBy(() -> service.pay(customer, ORDER_ID, "  "))
                .isInstanceOfSatisfying(ScmBusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(MallErrorCode.CHECKOUT_IDEMPOTENCY_REQUIRED));
        verify(salesOrderService, never()).lock(any());
        verify(paymentIntentService, never()).createFromMall(any(), any());
    }

    @Test
    void unconfirmedOrderCannotBePaid() {
        when(orderQueryService.requireOwnedOrder(customer, ORDER_ID)).thenReturn(order("PENDING", "WALLET"));
        when(salesOrderService.lock(ORDER_ID)).thenReturn(order("PENDING", "WALLET"));

        assertThatThrownBy(() -> service.pay(customer, ORDER_ID, "key"))
                .isInstanceOfSatisfying(ScmBusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(MallErrorCode.ORDER_NOT_PAYABLE));
        verify(paymentIntentService, never()).createFromMall(any(), any());
    }

    @Test
    void orderWithoutWalletSettlementCannotBePaidFromMall() {
        when(orderQueryService.requireOwnedOrder(customer, ORDER_ID)).thenReturn(order("CONFIRMED", "CREDIT"));
        when(salesOrderService.lock(ORDER_ID)).thenReturn(order("CONFIRMED", "CREDIT"));

        assertThatThrownBy(() -> service.pay(customer, ORDER_ID, "key"))
                .isInstanceOfSatisfying(ScmBusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(MallErrorCode.ORDER_SETTLEMENT_NOT_WALLET));
        verify(paymentIntentService, never()).createFromMall(any(), any());
    }

    @Test
    void settledOrderIsRejectedInsteadOfChargedTwice() {
        when(orderQueryService.requireOwnedOrder(customer, ORDER_ID)).thenReturn(order("CONFIRMED", "WALLET"));
        when(salesOrderService.lock(ORDER_ID)).thenReturn(order("CONFIRMED", "WALLET"));
        when(payableQueryService.payableOfConfirmedOrder(ORDER_ID)).thenReturn(new BigDecimal("60.0000"));
        when(payableQueryService.paidAmountOf(ORDER_ID)).thenReturn(new BigDecimal("60.0000"));

        assertThatThrownBy(() -> service.pay(customer, ORDER_ID, "key"))
                .isInstanceOfSatisfying(ScmBusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(MallErrorCode.ORDER_ALREADY_PAID));
        verify(paymentIntentService, never()).createFromMall(any(), any());
    }

    private static RequestMallCustomer customer() {
        RequestMallCustomer result = new RequestMallCustomer();
        result.setAccountId(7L);
        result.setCustomerId(9L);
        return result;
    }

    private static SalesOrderEntity order(String status, String settlementMethod) {
        SalesOrderEntity order = new SalesOrderEntity();
        order.setId(ORDER_ID);
        order.setCustomerId(9L);
        order.setStatus(status);
        order.setSettlementMethod(settlementMethod);
        return order;
    }
}
