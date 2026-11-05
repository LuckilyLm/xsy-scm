package com.xsy.scm.order.domain.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 订单行已完成分拣的实发数量；没有分拣结果的行不返回，调用方据此区分「还没分拣」与「分拣为 0」。
 */
@Data
public class SalesOrderSortedQuantityDto {
    private Long salesOrderItemId;
    private BigDecimal sortedQuantity;
}
