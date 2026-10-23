package com.xsy.scm.delivery.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 发车用分拣登记的实发量，不再复制计划量和分拣判断，免得有两套发货数量规则。
 */
@Data
public class DeliverySortedLine {
    private Long orderId;
    private Long salesOrderItemId;
    private Long skuId;
    private BigDecimal sortedQuantity;
    private String mallStockUnitSnapshot;
    private BigDecimal mallStockUnitsPerSaleUnit;
}
