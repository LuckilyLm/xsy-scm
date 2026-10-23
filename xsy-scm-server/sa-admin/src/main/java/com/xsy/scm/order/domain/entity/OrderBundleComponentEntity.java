package com.xsy.scm.order.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Data;

@Data
@TableName("xsy_order_bundle_component")
public class OrderBundleComponentEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long orderItemId;
    private Long warehouseId;
    private Long bundleSkuId;
    private Long componentSkuId;
    private String componentSkuCodeSnapshot;
    private String componentProductNameSnapshot;
    private String componentSaleUnitSnapshot;
    private String componentStockUnitSnapshot;
    private BigDecimal quantityPerBundleSaleUnit;
    private BigDecimal stockUnitsPerComponentSaleUnit;
    /**
     * 实际组成的销售数量（已包含组合套数）。
     */
    private BigDecimal componentSaleQuantitySnapshot;
    private BigDecimal marketUnitPriceSnapshot;
    private BigDecimal marketReferenceAmountSnapshot;
    private BigDecimal allocatedSaleAmountSnapshot;
    private Long productBundleComponentId;
    private Integer productBundleComponentVersion;
    private OffsetDateTime createdAt;
    private String createdBy;
}
