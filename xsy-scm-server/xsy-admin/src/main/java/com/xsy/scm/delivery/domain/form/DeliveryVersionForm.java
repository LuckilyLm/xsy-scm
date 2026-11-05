package com.xsy.scm.delivery.domain.form;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Data
public class DeliveryVersionForm {
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
    @Size(max = 500, message = "原因长度不能超过500")
    private String reason;
}
