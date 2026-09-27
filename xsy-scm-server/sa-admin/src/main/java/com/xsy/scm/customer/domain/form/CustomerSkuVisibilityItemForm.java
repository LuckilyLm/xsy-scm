package com.xsy.scm.customer.domain.form;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

@Data
public class CustomerSkuVisibilityItemForm {
    private Long id;
    private Integer version;
    @NotNull(message = "SKU ID 不能为空")
    private Long skuId;
}
