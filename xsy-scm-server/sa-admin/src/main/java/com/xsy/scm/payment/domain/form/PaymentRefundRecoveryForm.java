package com.xsy.scm.payment.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PaymentRefundRecoveryForm {
    @NotNull(message = "退款记录不能为空")
    @Positive(message = "退款记录 ID 必须大于0")
    private Long refundId;

    @NotBlank(message = "恢复原因不能为空")
    @Size(max = 500, message = "恢复原因不能超过500个字符")
    private String reason;
}
