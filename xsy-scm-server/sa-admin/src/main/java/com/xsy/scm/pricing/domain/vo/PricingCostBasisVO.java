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

    /**
     * 商品损耗率与税率（百分数，0-100），来自商品主档；计算时换算成系数。
     */
    private BigDecimal lossRate;
    private BigDecimal taxRate;

    /** 商品规格销售单位；与最近采购价单位不同时不能直接当销售单位成本。 */
    private String saleUnit;

    /**
     * 最近一条已提交采购行的采购单价；仅在新采购成本公式里有值。
     */
    private BigDecimal latestPurchasePrice;

    /** 最近采购价对应的采购单位。 */
    private String purchaseUnit;
}
