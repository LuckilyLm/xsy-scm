package com.xsy.scm.order.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrderReceivedCorrectionForm {
    @NotNull(message = "订单不能为空")
    private Long orderId;
    @NotNull(message = "订单明细不能为空")
    private Long orderItemId;
    @NotNull(message = "最新更正版本不能为空")
    @Min(value = 0, message = "最新更正版本不能小于0")
    private Long expectedLastCorrectionId;
    @NotBlank(message = "客户实收数量不能为空")
    private String receivedQuantity;
    @NotBlank(message = "更正原因不能为空")
    @Size(max = 500, message = "更正原因不能超过500个字符")
    private String reason;
}
