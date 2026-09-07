package com.xianshuyuan.scm.order.vo;

import com.xianshuyuan.scm.order.entity.OrderRefundStatus;

import java.time.OffsetDateTime;

public record OrderRefundResponse(Long id, String refundNo, Long returnId, Long orderId, Long customerId,
                                  String refundAmount, OrderRefundStatus status, String externalReference,
                                  OffsetDateTime completedAt, Integer version) {
}
