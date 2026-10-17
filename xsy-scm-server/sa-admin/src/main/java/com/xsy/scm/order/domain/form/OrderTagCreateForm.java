package com.xsy.scm.order.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrderTagCreateForm {
    @NotBlank(message = "标签名称不能为空")
    @Size(max = 32, message = "标签名称不能超过32个字符")
    private String name;

    @NotBlank(message = "标签颜色不能为空")
    private String color;
}
