package com.xsy.scm.mall.order.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MallOrderCancelForm {
    @NotBlank(message = "请选择或填写取消原因")
    @Size(max = 500, message = "取消原因不能超过500个字符")
    private String reason;
}
