package net.lab1024.sa.admin.module.scm.product.domain.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.*;
import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProductSpuUpdateForm extends ProductSpuAddForm {
    @NotNull
    @Positive
    private Long spuId;
    @NotNull
    @Min(0)
    private Integer version;
}
