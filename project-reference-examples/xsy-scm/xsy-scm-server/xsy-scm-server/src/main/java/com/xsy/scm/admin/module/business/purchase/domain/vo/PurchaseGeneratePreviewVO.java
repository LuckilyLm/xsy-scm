package com.xsy.scm.admin.module.business.purchase.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 采购单生成预览（按供应商分组）
 */
@Data
public class PurchaseGeneratePreviewVO {

    @Schema(description = "供应商ID")
    private Long supplierId;

    @Schema(description = "供应商名称")
    private String supplierName;

    @Schema(description = "采购明细预览")
    private List<PurchaseGeneratePreviewItemVO> items;
}
