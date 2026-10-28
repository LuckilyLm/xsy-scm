package com.xsy.scm.order.domain.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 独立退货建单：不关联原订单，直接按客户 + 商品明细申请。
 */
@Data
public class OrderReturnIndependentAddForm {
    @NotNull(message = "客户不能为空")
    private Long customerId;
    @NotBlank(message = "退货原因不能为空")
    @Size(max = 500, message = "退货原因不能超过500个字符")
    private String reason;
    @Valid
    @NotEmpty(message = "退货明细不能为空")
    @Size(max = 500, message = "退货明细不能超过500项")
    private List<OrderReturnIndependentItemForm> items;
}
