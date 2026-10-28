package com.xsy.scm.finance.domain.form;

import java.time.LocalDate;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;
import com.xsy.scm.common.util.ScmDecimalStrings;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改一张未作废的过账单。
 *
 * <p>
 * 客户不随修改改变：客户决定了这条账记在谁名下，换客户等于换一张单， 应该作废后重新登记，否则操作日志里会出现「同一个单号先后对应两个客户」。
 */
@Data
public class FinancePostingUpdateForm {

    @NotNull(message = "过账记录不能为空")
    @Positive(message = "过账记录编号必须大于0")
    private Long postingId;

    @NotNull(message = "版本号不能为空")
    @PositiveOrZero(message = "版本号不能为负数")
    private Integer version;

    @NotNull(message = "过账日期不能为空")
    private LocalDate documentDate;

    @NotNull(message = "过账金额不能为空")
    @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "过账金额格式无效")
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String amount;

    @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "税点格式无效")
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String taxRate;

    private String refundStatus;

    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}
