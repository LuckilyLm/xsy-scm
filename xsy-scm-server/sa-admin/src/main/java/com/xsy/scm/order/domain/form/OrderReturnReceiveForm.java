package com.xsy.scm.order.domain.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class OrderReturnReceiveForm {
    @NotNull(message = "退货单不能为空")
    @Positive(message = "退货单编号必须大于0")
    private Long returnId;
    @NotNull(message = "仓库不能为空")
    @Positive(message = "仓库编号必须大于0")
    private Long warehouseId;
    @NotNull(message = "退货单版本不能为空")
    @Min(value = 0, message = "退货单版本不能小于0")
    private Integer version;
    @Valid
    @NotEmpty(message = "接收明细不能为空")
    @Size(max = 500, message = "单次接收明细不能超过500项")
    private List<@NotNull(message = "接收明细不能为null") OrderReturnReceiveItemForm> items;
}
