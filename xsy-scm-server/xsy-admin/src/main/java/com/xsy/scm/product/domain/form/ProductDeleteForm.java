package com.xsy.scm.product.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ProductDeleteForm {
    @NotNull(message = "商品ID不能为空")
    @Positive(message = "商品ID必须大于0")
    private Long spuId;
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
}
