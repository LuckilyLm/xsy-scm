package com.xsy.scm.admin.module.business.purchase.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 采购单生成预览 - 明细行
 */
@Data
public class PurchaseGeneratePreviewItemVO {

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "商品名称")
    private String productName;

    @Schema(description = "规格ID")
    private Long skuId;

    @Schema(description = "订单汇总需求量")
    private BigDecimal requireQuantity;

    @Schema(description = "现有库存（calculateStock=true 时返回）")
    private BigDecimal stockQuantity;

    @Schema(description = "计划采购量（calculateStock 时=需求量-库存，否则=需求量）")
    private BigDecimal purchaseQuantity;

    @Schema(description = "参考供应价")
    private BigDecimal unitPrice;
}
