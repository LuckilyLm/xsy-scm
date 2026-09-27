package com.xsy.scm.order.domain.form;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;


@Data
public class OrderReturnAddForm {
    @NotNull(message = "订单 ID 不能为空")
    private Long orderId;
    @NotBlank(message = "退货原因不能为空")
    @Size(max = 500, message = "退货原因不能超过500个字符")
    private String reason;
    @Valid
    @NotEmpty(message = "退货明细不能为空")
    @Size(max = 500, message = "退货明细不能超过500项")
    private List<OrderReturnItemForm> items;
}
