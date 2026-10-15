package com.xsy.scm.mall.order.service;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.finance.service.FinanceOrderPayableQueryService;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.order.constant.ScmSettlementMethodEnum;
import com.xsy.scm.mall.order.domain.vo.MallOrderVO;
import com.xsy.scm.order.constant.ScmOrderStatusEnum;
import com.xsy.scm.order.domain.entity.SalesOrderEntity;
import com.xsy.scm.order.service.SalesOrderService;
import com.xsy.scm.payment.constant.ScmPaymentMethodEnum;
import com.xsy.scm.payment.constant.ScmPaymentProviderEnum;
import com.xsy.scm.payment.constant.ScmPaymentSourceTypeEnum;
import com.xsy.scm.payment.domain.form.PaymentIntentCreateForm;
import com.xsy.scm.payment.service.PaymentIntentService;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商城订单的客户侧支付：余额支付。
 *
 * <p>
 * 不新建支付状态机：扣钱包、登记 Finance 资金事实与订单资金核销编排全部走 {@link PaymentIntentService} 的既有链。 <b>金额由服务端在订单行锁内计算</b>（应付净额 −
 * 已付，与签收应收同一把尺子），客户端不传金额、 也不支持部分支付；钱包归属、余额不足与集团共享限制由余额域在消费时权威判定。
 */
@Service
@RequiredArgsConstructor
public class MallPaymentService {

    private final MallOrderQueryService orderQueryService;
    private final SalesOrderService salesOrderService;
    private final FinanceOrderPayableQueryService payableQueryService;
    private final PaymentIntentService paymentIntentService;
    private final ScmIdempotencyService idempotencyService;

    /**
     * 客户对「已确认且约定余额结算」的订单发起余额支付。
     *
     * <p>
     * 前置判定只为客户可读：权威门槛在支付意图创建与钱包消费。同键请求先回放已完成结果；不同键并发由订单行锁串行化，后到事务看到待付归零即拒绝。
     */
    @Transactional(rollbackFor = Exception.class)
    public MallOrderVO pay(RequestMallCustomer customer, Long orderId, String idempotencyKey) {
        // 扣钱必须有幂等保护，缺键直接拒绝，而不是放行一笔无法重放的支付
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ScmBusinessException(MallErrorCode.CHECKOUT_IDEMPOTENCY_REQUIRED);
        }
        orderQueryService.requireOwnedOrder(customer, orderId);
        var claim = idempotencyService.claim("MALL_WALLET_PAY:" + orderId, idempotencyKey,
                List.of(orderId, customer.getCustomerId()));
        if (claim.replay()) {
            return idempotencyService.replay(claim, MallOrderVO.class);
        }
        SalesOrderEntity order = salesOrderService.lock(orderId);
        if (!ScmOrderStatusEnum.CONFIRMED.name().equals(order.getStatus())) {
            throw new ScmBusinessException(MallErrorCode.ORDER_NOT_PAYABLE);
        }
        if (!ScmSettlementMethodEnum.WALLET.name().equals(order.getSettlementMethod())) {
            throw new ScmBusinessException(MallErrorCode.ORDER_SETTLEMENT_NOT_WALLET);
        }
        BigDecimal payable = payableQueryService.payableOfConfirmedOrder(orderId);
        BigDecimal due = payable.subtract(payableQueryService.paidAmountOf(orderId));
        if (due.signum() <= 0) {
            throw new ScmBusinessException(MallErrorCode.ORDER_ALREADY_PAID);
        }
        PaymentIntentCreateForm form = new PaymentIntentCreateForm();
        form.setSourceType(ScmPaymentSourceTypeEnum.SALES_ORDER.name());
        form.setSourceId(orderId);
        form.setCustomerId(order.getCustomerId());
        form.setAmount(due);
        form.setMethod(ScmPaymentMethodEnum.BALANCE.name());
        form.setProvider(ScmPaymentProviderEnum.INTERNAL_BALANCE.name());
        paymentIntentService.createFromMall(form, idempotencyKey);
        MallOrderVO result = orderQueryService.detail(customer, orderId);
        idempotencyService.complete(claim, "SALES_ORDER", orderId, result);
        return result;
    }
}
