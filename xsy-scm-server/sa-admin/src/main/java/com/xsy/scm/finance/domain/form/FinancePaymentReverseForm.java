package com.xsy.scm.finance.domain.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 反向一笔付款；原因缺失由服务返回财务错误码 41138。
 */
@Data
public class FinancePaymentReverseForm {

    @NotNull(message = "付款单不能为空")
    @Positive(message = "付款单编号必须大于0")
    private Long paymentId;

    @Size(max = 500, message = "反向原因长度不能超过500")
    private String reason;
}
