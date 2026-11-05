package com.xsy.scm.order.domain.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderExchangeCreateForm {
    @NotNull(message = "退货单不能为空")
    private Long returnId;
    @Valid
    @NotNull(message = "换货订单不能为空")
    private SalesOrderAddForm order;
}
