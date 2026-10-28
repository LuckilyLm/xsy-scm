package com.xsy.scm.finance.domain.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 作废一张过账单；原因由服务校验（空白按缺失处理，返回财务错误码而不是泛化的参数错误）。
 */
@Data
public class FinancePostingVoidForm {

    @NotNull(message = "过账记录不能为空")
    @Positive(message = "过账记录编号必须大于0")
    private Long postingId;

    @NotNull(message = "版本号不能为空")
    @PositiveOrZero(message = "版本号不能为负数")
    private Integer version;

    @Size(max = 500, message = "作废原因长度不能超过500")
    private String reason;
}
