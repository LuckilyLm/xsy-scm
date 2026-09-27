package com.xsy.scm.order.domain.form;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;


@Data
public class OrderBatchDeleteForm {
    @Valid
    @NotEmpty(message = "订单列表不能为空")
    @Size(max = 100, message = "一次最多删除100个订单")
    private List<OrderVersionForm> orders;
}
