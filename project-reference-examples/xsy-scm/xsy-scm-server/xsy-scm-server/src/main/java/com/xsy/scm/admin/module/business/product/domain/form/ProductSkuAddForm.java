package com.xsy.scm.admin.module.business.product.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品规格 SKU 添加表单
 *
 * @author xsy-scm
 */
@Data
public class ProductSkuAddForm {

    @Schema(description = "所属商品ID")
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @Schema(description = "规格名称，如 规格/单位 组合")
    @NotBlank(message = "规格名称不能为空")
    private String specName;

    @Schema(description = "销售单位")
    @NotBlank(message = "销售单位不能为空")
    private String unit;

    @Schema(description = "单件折算重量（kg），非标品换算用")
    private BigDecimal unitWeight;

    @Schema(description = "状态：1 启用，2 停用")
    private Integer status;
}
