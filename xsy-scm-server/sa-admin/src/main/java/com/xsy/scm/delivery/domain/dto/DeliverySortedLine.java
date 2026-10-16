package com.xsy.scm.delivery.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 配送发车使用分拣已登记的实发量，不复制计划量与分拣结果判定，避免两套发货数量口径。 */
@Data
public class DeliverySortedLine {
    private Long orderId;
    private Long salesOrderItemId;
    private Long skuId;
    private BigDecimal sortedQuantity;
    private String mallStockUnitSnapshot;
    private BigDecimal mallStockUnitsPerSaleUnit;
}
