package com.xianshuyuan.scm.order.vo;

import com.xianshuyuan.scm.order.entity.*;

import java.time.OffsetDateTime;
import java.util.List;

public record OrderReturnResponse(Long id, String returnNo, Long orderId, Long customerId, OrderReturnStatus status,
                                  String reason, String decisionReason, String approvedAmount, Integer version,
                                  OffsetDateTime createdAt, List<Item> items, OrderRefundResponse refund) {
    public record Item(Long id, Long orderItemId, String requestedQuantity, String approvedQuantity,
                       String lockedUnitPrice, String approvedAmount, Integer version) {
    }
}
