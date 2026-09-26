package com.xsy.scm.product.domain.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.*;
import java.math.BigDecimal;

@Data
public class ProductDeleteForm {
    @NotNull
    @Positive
    private Long spuId;
    @NotNull
    @Min(0)
    private Integer version;
}
