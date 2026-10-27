package com.xsy.scm.finance.domain.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 反向一条抹零；原因由服务校验，缺失时返回财务错误码。
 */
@Data
public class FinanceWaiverReverseForm {

    @NotNull(message = "抹零记录不能为空")
    @Positive(message = "抹零记录编号必须大于0")
    private Long waiverId;

    @Size(max = 500, message = "反向原因长度不能超过500")
    private String reason;
}
