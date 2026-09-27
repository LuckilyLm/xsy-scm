package com.xsy.scm.order.domain.form;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Data
public class OrderReturnDecisionForm {
    @NotNull(message = "退货单 ID 不能为空")
    private Long returnId;
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
    @NotBlank(message = "处理意见不能为空")
    @Size(max = 500, message = "处理意见不能超过500个字符")
    private String decisionReason;
}
