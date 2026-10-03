package com.xsy.scm.payment.support;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 已持久化的余额消费事实。 */
public record BalanceConsumptionResult(Long movementId, Long intentId, Long customerId, Long settlementCustomerId,
        BigDecimal amount, OffsetDateTime occurredAt) {
}
