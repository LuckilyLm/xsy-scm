package com.xsy.scm.order.domain.form;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Data
public class OrderRefundCompleteForm {
    @NotNull(message = "退款单 ID 不能为空")
    private Long refundId;
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
    @Size(max = 128, message = "外部凭证号不能超过128个字符")
    private String externalReference;
}
