package com.xsy.scm.order.domain.form;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;


import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;

@Data
public class OrderReturnItemForm {
    @NotNull(message = "订单明细 ID 不能为空")
    private Long orderItemId;
    @NotBlank(message = "申请退货数量不能为空")
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String requestedQuantity;
}
