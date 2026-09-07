package com.xianshuyuan.scm.order.dto;

import com.xianshuyuan.scm.order.entity.OrderRefundStatus;

public record OrderRefundPageQuery(long page, long pageSize, String keyword, OrderRefundStatus status, Long orderId,
                                   Long returnId, Long customerId) {
}
