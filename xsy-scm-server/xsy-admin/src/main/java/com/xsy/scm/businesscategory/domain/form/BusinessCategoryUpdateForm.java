package com.xsy.scm.businesscategory.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class BusinessCategoryUpdateForm extends BusinessCategoryAddForm {

    @NotNull(message = "分类 ID 不能为空")
    @Positive(message = "分类 ID 必须大于0")
    private Long categoryId;

    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
}
