package com.xsy.scm.order.domain.dto;

import java.math.BigDecimal;

public record OrderRefundBalanceFact(Long refundId, Long movementId, BigDecimal amount) {
}
