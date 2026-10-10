package com.xsy.scm.payment.domain.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class RefundFundingDetailForm {
    @NotNull(message = "退款单不能为空")
    @Positive(message = "退款单 ID 必须大于0")
    private Long refundId;
}
