package com.xsy.scm.finance.domain.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 反向一条正常核销；原因缺失由服务返回财务错误码 41138。 */
@Data
public class FinanceWriteOffReverseForm {

    @NotNull(message = "核销记录不能为空")
    @Positive(message = "核销记录编号必须大于0")
    private Long writeOffId;

    @Size(max = 500, message = "反向原因长度不能超过500")
    private String reason;
}
