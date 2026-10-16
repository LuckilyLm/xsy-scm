package com.xsy.scm.pricing.domain.form;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;
import com.xsy.scm.common.util.ScmDecimalStrings;
import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.pricing.constant.AgreementFormulaType;

import java.time.OffsetDateTime;

@Data
public class AgreementPriceAddForm {
    @NotNull(message = "客户不能为空")
    private Long customerId;
    @NotNull(message = "商品规格不能为空")
    private Long skuId;
    @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "协议价格式不正确")
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String unitPrice;
    @ScmEnumValue(enumClass = AgreementFormulaType.class, message = "协议价公式类型无效")
    private String formulaType;
    @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "公式参数格式不正确")
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String formulaParameter;
    @NotNull(message = "生效开始时间不能为空")
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime effectiveTo;
}
