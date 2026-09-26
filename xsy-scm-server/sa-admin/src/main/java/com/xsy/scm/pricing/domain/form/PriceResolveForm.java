package com.xsy.scm.pricing.domain.form;

import lombok.Data;
import jakarta.validation.constraints.*;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class PriceResolveForm {
    @NotNull
    private Long customerId;
    private OffsetDateTime at;
    @NotNull
    @NotEmpty
    @Size(max = 500)
    private List<@NotNull Long> skuIds;
}
