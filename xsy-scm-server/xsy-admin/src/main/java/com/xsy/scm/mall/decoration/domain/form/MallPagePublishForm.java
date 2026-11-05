package com.xsy.scm.mall.decoration.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

@Data
public class MallPagePublishForm {
    @NotBlank(message = "页面编码不能为空")
    private String pageCode;
    @NotNull(message = "版本不能为空")
    @Min(value = 0, message = "版本不能小于0")
    private Integer version;
}
