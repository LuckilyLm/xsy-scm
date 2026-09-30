package com.xsy.scm.finance.domain.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 反向一笔收款；原因缺失由服务返回财务错误码 41138。
 */
@Data
public class FinanceReceiptReverseForm {

    @NotNull(message = "收款单不能为空")
    @Positive(message = "收款单编号必须大于0")
    private Long receiptId;

    @Size(max = 500, message = "反向原因长度不能超过500")
    private String reason;
}
