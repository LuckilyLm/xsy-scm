package com.xsy.scm.admin.module.business.purchase.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 采购明细 添加表单
 *
 * @author xsy-scm
 */
@Data
public class PurchaseItemAddForm {

    @Schema(description = "采购单ID")
    @NotNull(message = "采购单ID不能为空")
    private Long purchaseId;

    @Schema(description = "商品ID")
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @Schema(description = "规格ID")
    private Long skuId;

    @Schema(description = "需求量（订单汇总得出）")
    private BigDecimal requireQuantity;

    @Schema(description = "计划采购量")
    private BigDecimal purchaseQuantity;

    @Schema(description = "累计已收数量")
    private BigDecimal receivedQuantity;

    @Schema(description = "采购单价（不含税）")
    private BigDecimal unitPrice;

    @Schema(description = "状态：1 待收，2 部分收，3 已收齐")
    private Integer status;
}
