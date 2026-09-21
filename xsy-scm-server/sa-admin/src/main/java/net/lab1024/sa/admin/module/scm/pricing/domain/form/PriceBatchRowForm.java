package net.lab1024.sa.admin.module.scm.pricing.domain.form;

import lombok.Data;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmStrictDecimalStringDeserializer;

@Data
public class PriceBatchRowForm {
    private Integer rowNumber;
    private Long customerTypeId;
    private Long skuId;
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String unitPrice;
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime effectiveTo;
}
