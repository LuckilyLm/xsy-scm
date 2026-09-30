package com.xsy.scm.finance.domain.form;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;
import com.xsy.scm.common.util.ScmDecimalStrings;

/** 一条核销目标及本次分配金额。 */
@Data
public class FinanceWriteOffAddItemForm {

    @NotNull(message = "核销目标不能为空")
    @Positive(message = "核销目标编号必须大于0")
    private Long targetId;

    @NotNull(message = "核销金额不能为空")
    @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "核销金额格式无效")
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String amount;
}
