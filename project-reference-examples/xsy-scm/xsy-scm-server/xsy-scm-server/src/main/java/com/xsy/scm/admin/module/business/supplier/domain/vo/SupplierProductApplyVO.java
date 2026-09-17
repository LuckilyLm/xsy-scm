package com.xsy.scm.admin.module.business.supplier.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 供应商商品提报 返回对象
 *
 * @author xsy-scm
 */
@Data
public class SupplierProductApplyVO {

    @Schema(description = "主键ID")
    private Long applyId;

    @Schema(description = "提报单号")
    private String applyNo;

    @Schema(description = "供应商ID")
    private Long supplierId;

    @Schema(description = "商品名称")
    private String productName;

    @Schema(description = "商品别名")
    private String alias;

    @Schema(description = "拟归类ID")
    private Long categoryId;

    @Schema(description = "供货价（不含税）")
    private BigDecimal supplyPrice;

    @Schema(description = "最近一次进价（不含税）")
    private BigDecimal lastPurchasePrice;

    @Schema(description = "商品图片")
    private String image;

    @Schema(description = "审核状态：1 待审核，2 已通过，3 已驳回")
    private Integer auditStatus;

    @Schema(description = "驳回原因")
    private String rejectReason;

    @Schema(description = "审核通过后生成的商品ID")
    private Long productId;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
