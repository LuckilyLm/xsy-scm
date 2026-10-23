package com.xsy.scm.balance.domain.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 退款与原订单的固定归属；余额返还不能用客户当前的集团关系改派余额账户。
 */
public record BalanceRefundFact(Long refundId, Long orderId, Long customerId, Long settlementCustomerId, Long sellerId,
    BigDecimal amount, String status, OffsetDateTime completedAt) {
}
