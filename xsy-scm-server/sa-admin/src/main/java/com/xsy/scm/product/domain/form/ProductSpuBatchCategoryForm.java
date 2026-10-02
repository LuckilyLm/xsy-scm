package com.xsy.scm.product.domain.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ProductSpuBatchCategoryForm {
    @NotEmpty(message = "批量项目列表不能为空")
    @Size(max = 200, message = "批量项目列表不能超过200项")
    @Valid
    private List<ProductBatchItemForm> items;
    @NotNull(message = "分类 ID不能为空")
    @Positive(message = "分类 ID必须大于0")
    private Long categoryId;
}
