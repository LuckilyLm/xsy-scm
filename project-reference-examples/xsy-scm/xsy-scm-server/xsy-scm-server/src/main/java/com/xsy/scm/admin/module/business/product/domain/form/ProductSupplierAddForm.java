package com.xsy.scm.admin.module.business.product.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品-供应商关系 添加表单
 *
 * @author xsy-scm
 */
@Data
public class ProductSupplierAddForm {

    @Schema(description = "商品ID")
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @Schema(description = "供应商ID")
    @NotNull(message = "供应商ID不能为空")
    private Long supplierId;

    @Schema(description = "供应价（不含税）")
    @NotNull(message = "供应价不能为空")
    private BigDecimal supplyPrice;

    @Schema(description = "是否默认供应商")
    private Boolean defaultFlag;
}
