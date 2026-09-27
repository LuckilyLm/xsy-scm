package com.xsy.scm.order.domain.form;

import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;



@Data
@EqualsAndHashCode(callSuper = true)
public class SalesOrderUpdateForm extends SalesOrderAddForm {
    @NotNull(message = "订单 ID 不能为空")
    private Long orderId;
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
}
