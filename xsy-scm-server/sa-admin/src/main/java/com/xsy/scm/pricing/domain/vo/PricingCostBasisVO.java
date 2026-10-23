package com.xsy.scm.pricing.domain.vo;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class PricingCostBasisVO {
    private Long skuId;
    private Long warehouseId;
    private String stockUnit;
    private BigDecimal stockUnitCost;
    private BigDecimal stockUnitsPerSaleUnit;
    private Integer balanceVersion;
}
