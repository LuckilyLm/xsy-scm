package net.lab1024.sa.admin.module.scm.product.domain.form;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ProductUomKeyForm {
    @NotNull
    @Positive
    private Long uomId;
    @NotNull
    @Min(0)
    private Integer version;
}
