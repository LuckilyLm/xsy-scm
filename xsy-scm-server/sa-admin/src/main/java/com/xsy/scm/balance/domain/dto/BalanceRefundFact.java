package com.xsy.scm.balance.domain.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 退款与原订单冻结身份；钱包返还不能使用客户当前集团关系改派钱包。 */
public record BalanceRefundFact(Long refundId, Long orderId, Long customerId, Long settlementCustomerId,
        Long sellerId, BigDecimal amount, String status, OffsetDateTime completedAt) {
}
