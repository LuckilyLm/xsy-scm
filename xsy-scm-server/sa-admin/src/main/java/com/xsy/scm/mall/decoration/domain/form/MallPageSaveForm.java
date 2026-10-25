package com.xsy.scm.mall.decoration.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

@Data
public class MallPageSaveForm {
    @NotBlank(message = "页面编码不能为空")
    private String pageCode;
    @NotNull(message = "版本不能为空")
    @Min(value = 0, message = "版本不能小于0")
    private Integer version;
    @NotBlank(message = "标题不能为空")
    @Size(max = 80, message = "标题不能超过80个字符")
    private String title;
    @Size(max = 200000, message = "正文不能超过200000个字符")
    private String content;
}
