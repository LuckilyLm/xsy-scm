package com.xsy.scm.report.domain.dto;

import java.math.BigDecimal;

import lombok.Data;

/** 按仓库、采购员、SKU 与采购单位保存，读取时先收窄授权范围再合并。 */
@Data
public class PurchaseDailySnapshotRow {
    private Long warehouseId;
    private Long purchaserId;
    private Long skuId;
    private String spuCode;
    private String productName;
    private String skuCode;
    private String skuName;
    private String purchaseUnit;
    private Long orderCount;
    private BigDecimal plannedQuantity;
    private BigDecimal orderAmount;
}
