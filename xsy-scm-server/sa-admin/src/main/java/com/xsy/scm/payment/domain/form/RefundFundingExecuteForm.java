package com.xsy.scm.payment.domain.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class RefundFundingExecuteForm {
    @NotNull(message = "退款单不能为空")
    @Positive(message = "退款单 ID 必须大于0")
    private Long refundId;

    /** 仅 mock 渠道失败场景回归使用。 */
    private String mockScenario;
}
