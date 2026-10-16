package com.xsy.scm.inventory.domain.vo;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class BundleSalesOutAllocationVO {
    private Long movementId;
    private Long outboundItemId;
    private Long bundleComponentId;
    private Long skuId;
    private BigDecimal quantity;
    private String unitSnapshot;
    private BigDecimal unitCost;
}
