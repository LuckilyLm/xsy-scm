package com.xsy.scm.product.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductSkuStockConversionDeleteForm {
    @NotNull(message = "商品规格不能为空")
    private Long skuId;
    @NotBlank(message = "库存记账单位不能为空")
    @Size(max = 32, message = "库存记账单位不能超过32个字符")
    private String stockUnit;
}
