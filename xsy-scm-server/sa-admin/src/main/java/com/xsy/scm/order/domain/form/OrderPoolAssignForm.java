package com.xsy.scm.order.domain.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class OrderPoolAssignForm {
    @NotNull(message = "订单不能为空")
    @Positive(message = "订单 ID 必须大于0")
    private Long orderId;

    @NotNull(message = "目标业务员不能为空")
    @Positive(message = "目标业务员 ID 必须大于0")
    private Long sellerId;

    @NotNull(message = "订单版本不能为空")
    @Min(value = 0, message = "订单版本不能小于0")
    private Integer version;
}
