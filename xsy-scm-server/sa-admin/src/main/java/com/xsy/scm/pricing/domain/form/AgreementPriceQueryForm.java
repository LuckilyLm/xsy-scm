package com.xsy.scm.pricing.domain.form;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;
import java.time.OffsetDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class AgreementPriceQueryForm extends PageParam {
    private Long customerId;
    private Long skuId;
    @Size(max = 150, message = "关键字不能超过150个字符")
    private String keyword;
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime effectiveTo;
}
