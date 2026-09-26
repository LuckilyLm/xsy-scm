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
@EqualsAndHashCode(callSuper = true)
public class CustomerTypePriceQueryForm extends PageParam {
    private Long customerTypeId;
    private Long skuId;
    @Size(max = 150)
    private String keyword;
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime effectiveTo;
}
