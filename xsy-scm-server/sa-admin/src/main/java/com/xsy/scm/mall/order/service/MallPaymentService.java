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
 * 商城余额支付沿用现有支付与余额服务，不另建状态机。应付金额在服务端锁定订单后计算，客户端不能指定金额； 余额及集团共享权限在扣减余额时校验。
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
     * 前置判定只为给出客户可读的提示：真正的限制在支付意图创建与余额扣减。同键请求先回放已完成结果； 不同键并发由订单行锁串行化，后到事务看到待付归零即拒绝。
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
        idempotencyService.complete(claim, ScmPaymentSourceTypeEnum.SALES_ORDER.name(), orderId, result);
        return result;
    }
}
