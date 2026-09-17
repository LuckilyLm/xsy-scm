package com.xsy.scm.admin.module.business.purchase.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采购明细 返回对象
 *
 * @author xsy-scm
 */
@Data
public class PurchaseItemVO {

    @Schema(description = "明细ID")
    private Long itemId;

    @Schema(description = "采购单ID")
    private Long purchaseId;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "规格ID")
    private Long skuId;

    @Schema(description = "绑定供应商ID")
    private Long supplierId;

    @Schema(description = "需求量")
    private BigDecimal requireQuantity;

    @Schema(description = "计划采购量")
    private BigDecimal purchaseQuantity;

    @Schema(description = "累计已收数量")
    private BigDecimal receivedQuantity;

    @Schema(description = "采购单价（不含税）")
    private BigDecimal unitPrice;

    @Schema(description = "状态：1 待收，2 部分收，3 已收齐")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
