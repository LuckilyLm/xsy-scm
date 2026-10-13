package com.xsy.scm.payment.support;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 支付退款编排写入钱包返还的领域契约。
 *
 * <p>
 * {@code occurredAt} 是<b>资金实际返还时刻</b>，不是退款单的业务完成时刻：余额返还由人工与重试入口触发，
 * 钱包真正加钱发生在调用那一刻。它同时是客户流水与商城「资金到账时间」的取数来源，写错会让两者都指向退款单完成时刻。
 */
public interface RefundBalanceSink {
    Long refundAllocation(Long allocationId, Long customerId, Long settlementCustomerId, BigDecimal amount,
            OffsetDateTime occurredAt);
}
