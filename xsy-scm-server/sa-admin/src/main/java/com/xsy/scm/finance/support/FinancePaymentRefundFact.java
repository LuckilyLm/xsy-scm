package com.xsy.scm.finance.support;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 「一笔渠道退款已成功」的**事实**：支付域交给财务域的最小字段集。
 *
 * <p>
 * 与收款的 {@link FinancePaymentReceiptFact} 对称：不让支付域拼一个人工表单
 * （{@code FinancePaymentAddForm} 的字段假设「有人在填」），系统来源的方式、时点、外部凭据
 * 由财务域自己决定。
 *
 * @param paymentRefundId
 *            {@code payment_refund.id}；财务域用它反查来源键，也是恢复路径的幂等依据
 * @param orderRefundId
 *            业务退款单 id；财务域据此生成 {@code source_type = ORDER_REFUND} 的来源键，
 *            并**再校验一遍**退款单的客户、状态与应退金额
 * @param customerId
 *            退款对象客户；必须与业务退款单的客户一致
 * @param providerAmount
 *            **渠道实际退回的金额**。财务付款金额认它，不认申请额
 * @param refundedAt
 *            渠道退款成功时间（不是本地登记时间）
 * @param providerRefundNo
 *            渠道退款号，落进 {@code external_reference} 供人工核对
 */
public record FinancePaymentRefundFact(Long paymentRefundId, Long orderRefundId, Long customerId,
        BigDecimal providerAmount, OffsetDateTime refundedAt, String providerRefundNo) {
}
