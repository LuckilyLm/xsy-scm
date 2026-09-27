package com.xsy.scm.order.domain.form;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;


@Data
public class OrderReturnApproveForm {
    @NotNull(message = "退货单 ID 不能为空")
    private Long returnId;
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
    @Valid
    @NotEmpty(message = "退货审批明细不能为空")
    @Size(max = 500, message = "退货审批明细不能超过500项")
    private List<OrderReturnApproveItemForm> items;
}
