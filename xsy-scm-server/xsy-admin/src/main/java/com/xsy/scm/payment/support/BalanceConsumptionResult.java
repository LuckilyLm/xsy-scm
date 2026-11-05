package com.xsy.scm.payment.support;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 已写入的余额消费记录。
 */
public record BalanceConsumptionResult(Long movementId, Long intentId, Long customerId, Long settlementCustomerId,
    BigDecimal amount, OffsetDateTime occurredAt) {
}
