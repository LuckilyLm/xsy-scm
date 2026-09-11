package com.xianshuyuan.scm.mall.vo;

import com.xianshuyuan.scm.order.entity.OrderStatus;

public record MallOrderSubmitResponse(Long orderId, String orderNo, OrderStatus status, String totalAmount) {
}
