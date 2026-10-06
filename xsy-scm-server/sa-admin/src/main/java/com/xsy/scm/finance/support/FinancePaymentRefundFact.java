package com.xsy.scm.finance.support;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 支付域交给财务域的退款事实：一笔渠道退款已成功。
 *
 * <p>
 * 与 {@link FinancePaymentReceiptFact} 对称：系统来源的方式、时点与外部凭据由财务域自行决定，不复制到支付域。
 */
public record FinancePaymentRefundFact(Long paymentRefundId, Long orderRefundId, Long customerId,
        BigDecimal providerAmount, OffsetDateTime refundedAt, String providerRefundNo) {
}
