package com.xsy.scm.admin.module.business.supplier.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 供应商商品提报 添加表单
 *
 * @author xsy-scm
 */
@Data
public class SupplierProductApplyAddForm {

    @Schema(description = "供应商ID")
    @NotNull(message = "供应商ID不能为空")
    private Long supplierId;

    @Schema(description = "商品名称")
    @NotBlank(message = "商品名称不能为空")
    private String productName;

    @Schema(description = "商品别名（≤20 字）")
    private String alias;

    @Schema(description = "拟归类ID")
    private Long categoryId;

    @Schema(description = "供货价（不含税）")
    private BigDecimal supplyPrice;

    @Schema(description = "商品图片")
    private String image;
}
