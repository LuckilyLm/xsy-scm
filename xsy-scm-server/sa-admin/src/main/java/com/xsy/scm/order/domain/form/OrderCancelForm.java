package com.xsy.scm.order.domain.form;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Data
public class OrderCancelForm {
    @NotNull(message = "订单 ID 不能为空")
    private Long orderId;
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
    @NotBlank(message = "取消原因不能为空")
    @Size(max = 500, message = "取消原因不能超过500个字符")
    private String reason;
}
