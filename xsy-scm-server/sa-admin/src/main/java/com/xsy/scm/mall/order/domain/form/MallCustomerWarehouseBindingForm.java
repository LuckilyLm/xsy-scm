package com.xsy.scm.mall.order.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class MallCustomerWarehouseBindingForm {
    @NotNull(message = "客户不能为空")
    @Positive(message = "客户必须大于0")
    private Long customerId;

    @Positive(message = "配送仓必须大于0")
    private Long warehouseId;

    @NotNull(message = "客户版本不能为空")
    @Min(value = 0, message = "客户版本不能小于0")
    private Integer version;
}
