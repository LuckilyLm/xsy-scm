package com.xsy.scm.product.domain.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.*;
import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProductStatusForm extends ProductDeleteForm {
    @NotNull
    @Pattern(regexp = "ON_SHELF|OFF_SHELF")
    private String status;
}
