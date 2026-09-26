package com.xsy.scm.product.domain.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.*;

@Data
public class ProductSpuBatchCategoryForm {
    @NotEmpty
    @Size(max = 200)
    @Valid
    private List<ProductBatchItemForm> items;
    @NotNull
    @Positive
    private Long categoryId;
}
