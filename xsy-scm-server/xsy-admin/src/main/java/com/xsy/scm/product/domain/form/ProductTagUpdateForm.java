package com.xsy.scm.product.domain.form;

import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProductTagUpdateForm extends ProductTagAddForm {
    @NotNull(message = "标签 ID不能为空")
    @Positive(message = "标签 ID必须大于0")
    private Long tagId;
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
}
