package net.lab1024.sa.admin.module.scm.product.domain.form;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ProductTagKeyForm {
    @NotNull
    @Positive
    private Long tagId;
    @NotNull
    @Min(0)
    private Integer version;
}
