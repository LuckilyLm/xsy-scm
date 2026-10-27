package com.xsy.scm.finance.domain.form;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;
import com.xsy.scm.common.util.ScmDecimalStrings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 对一张正常应收登记抹零。
 *
 * <p>
 * 金额走字符串解析，避免前端把 {@code 0.30} 传成浮点后在四位小数上抖动；原因必填，对账单要能解释这笔减免。
 */
@Data
public class FinanceWaiverAddForm {

    @NotNull(message = "抹零应收不能为空")
    @Positive(message = "抹零应收编号必须大于0")
    private Long receivableId;

    @NotNull(message = "抹零金额不能为空")
    @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "抹零金额格式无效")
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String amount;

    @NotBlank(message = "抹零原因不能为空")
    @Size(max = 500, message = "抹零原因长度不能超过500")
    private String reason;
}
