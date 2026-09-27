package com.xsy.scm.pricing.domain.form;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;
import com.xsy.scm.common.util.ScmDecimalStrings;

import java.time.OffsetDateTime;

@Data
public class AgreementPriceAddForm {
    @NotNull(message = "客户不能为空")
    private Long customerId;
    @NotNull(message = "SKU 不能为空")
    private Long skuId;
    @NotNull(message = "协议价不能为空")
    @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "协议价格式不正确")
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String unitPrice;
    @NotNull(message = "生效开始时间不能为空")
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime effectiveTo;
}
