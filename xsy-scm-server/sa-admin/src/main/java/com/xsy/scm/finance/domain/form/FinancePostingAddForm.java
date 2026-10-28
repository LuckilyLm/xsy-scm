package com.xsy.scm.finance.domain.form;

import java.time.LocalDate;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;
import com.xsy.scm.common.util.ScmDecimalStrings;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登记一张过账特殊单据。
 *
 * <p>
 * 金额与税点都走定点字符串：前端传浮点会在四位小数上抖动，而过账金额是要和纸质单据逐位对上的。 税点可为空（单据上没写就不填），不据此计算税额。
 */
@Data
public class FinancePostingAddForm {

    @NotNull(message = "过账客户不能为空")
    @Positive(message = "过账客户编号必须大于0")
    private Long customerId;

    @NotNull(message = "过账日期不能为空")
    private LocalDate documentDate;

    @NotNull(message = "过账金额不能为空")
    @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "过账金额格式无效")
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String amount;

    @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "税点格式无效")
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String taxRate;

    /**
     * {@code PENDING / PARTIAL / REFUNDED}；不填按未返款登记。
     */
    private String refundStatus;

    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}
