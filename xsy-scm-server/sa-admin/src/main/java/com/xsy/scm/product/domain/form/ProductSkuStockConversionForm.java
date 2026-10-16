package com.xsy.scm.product.domain.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class ProductSkuStockConversionForm {
    @NotNull(message = "商品规格不能为空")
    private Long skuId;
    @NotBlank(message = "库存记账单位不能为空")
    @Size(max = 32, message = "库存记账单位不能超过32个字符")
    private String stockUnit;
    @NotNull(message = "单位换算比例不能为空")
    @DecimalMin(value = "0.0001", message = "单位换算比例必须大于0")
    @Digits(integer = 14, fraction = 4, message = "单位换算比例最多14位整数和4位小数")
    private BigDecimal stockUnitsPerSaleUnit;
}
