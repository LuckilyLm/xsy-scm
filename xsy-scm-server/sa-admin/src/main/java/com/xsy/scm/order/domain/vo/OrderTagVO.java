package com.xsy.scm.order.domain.vo;

import lombok.Data;

/**
 * 订单辅助标签，不承载订单履约或价格状态。
 */
@Data
public class OrderTagVO {
    private Long orderId;
    private Long tagId;
    private String name;
    private String color;
}
