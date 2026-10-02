package com.xsy.scm.order.domain.form;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrderConfirmForm extends OrderVersionForm {
    private Boolean creditOverride;
    @Size(max = 500, message = "授信例外原因不能超过500个字符")
    private String creditOverrideReason;
}
