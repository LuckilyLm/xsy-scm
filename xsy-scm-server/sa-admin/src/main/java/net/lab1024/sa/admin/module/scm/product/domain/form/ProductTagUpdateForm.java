package net.lab1024.sa.admin.module.scm.product.domain.form;

import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.*;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProductTagUpdateForm extends ProductTagAddForm {
    @NotNull
    @Positive
    private Long tagId;
    @NotNull
    @Min(0)
    private Integer version;
}
