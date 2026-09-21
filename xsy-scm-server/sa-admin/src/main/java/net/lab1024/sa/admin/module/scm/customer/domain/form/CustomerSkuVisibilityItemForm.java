package net.lab1024.sa.admin.module.scm.customer.domain.form;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

@Data
public class CustomerSkuVisibilityItemForm {
    private Long id;
    private Integer version;
    @NotNull
    private Long skuId;
}
