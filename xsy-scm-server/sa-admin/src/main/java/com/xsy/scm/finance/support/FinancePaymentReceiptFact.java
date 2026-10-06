package com.xsy.scm.finance.support;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 支付域交给财务域的收款事实：一笔在线支付已成功收款。
 *
 * <p>
 * 系统来源的收款不走 {@code FinanceReceiptAddForm}（那是人工登记入口，字段假设「有人在填」）：方式、结算客户快照与来源键都由财务域自行决定，不复制到支付域。
 */
public record FinancePaymentReceiptFact(Long transactionId, Long customerId, BigDecimal providerAmount,
        OffsetDateTime succeededAt, String providerTransactionNo) {
}
