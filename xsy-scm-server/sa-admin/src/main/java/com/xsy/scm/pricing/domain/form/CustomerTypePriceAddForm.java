package com.xsy.scm.pricing.domain.form;

import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;
import com.xsy.scm.common.util.ScmDecimalStrings;
import net.lab1024.sa.base.common.domain.PageParam;

import java.time.OffsetDateTime;

@Data
public class CustomerTypePriceAddForm {
    @NotNull
    private Long customerTypeId;
    @NotNull
    private Long skuId;
    @NotNull
    @Pattern(regexp = ScmDecimalStrings.PATTERN)
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String unitPrice;
    @NotNull
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime effectiveTo;
}
