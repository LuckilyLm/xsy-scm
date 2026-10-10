package com.xsy.scm.payment.support;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 支付退款编排写入钱包返还的领域契约。 */
public interface RefundBalanceSink {
    Long refundAllocation(Long allocationId, Long customerId, Long settlementCustomerId, BigDecimal amount,
            OffsetDateTime occurredAt);
}
