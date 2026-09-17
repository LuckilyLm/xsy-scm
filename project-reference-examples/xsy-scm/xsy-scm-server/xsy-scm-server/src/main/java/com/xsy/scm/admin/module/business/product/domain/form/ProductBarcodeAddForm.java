package com.xsy.scm.admin.module.business.product.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 商品条码 添加表单
 *
 * @author xsy-scm
 */
@Data
public class ProductBarcodeAddForm {

    @Schema(description = "商品ID")
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @Schema(description = "规格ID")
    private Long skuId;

    @Schema(description = "条形码")
    @NotBlank(message = "条形码不能为空")
    private String barcode;

    @Schema(description = "对应单位")
    private String unit;
}
