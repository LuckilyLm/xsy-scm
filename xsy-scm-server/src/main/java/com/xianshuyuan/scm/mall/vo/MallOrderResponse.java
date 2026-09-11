package com.xianshuyuan.scm.mall.vo;

import com.xianshuyuan.scm.order.entity.OrderSource;
import com.xianshuyuan.scm.order.entity.OrderStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record MallOrderResponse(Long id, String orderNo, OrderStatus status, OrderSource source, String totalAmount,
                                OffsetDateTime createdAt, OffsetDateTime submittedAt, OffsetDateTime confirmedAt,
                                List<MallOrderItemResponse> items) {
}
