package com.xsy.scm.order.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrderRepriceForm extends OrderVersionForm {
    @NotBlank(message = "价格重算原因不能为空")
    @Size(max = 500, message = "价格重算原因不能超过500个字符")
    private String reason;
}
