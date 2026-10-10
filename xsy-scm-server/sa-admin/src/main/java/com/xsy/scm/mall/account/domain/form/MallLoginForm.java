package com.xsy.scm.mall.account.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MallLoginForm {
    @NotBlank(message = "手机号不能为空")
    @Size(max = 32, message = "手机号不能超过32个字符")
    private String phone;
    @NotBlank(message = "密码不能为空")
    @Size(max = 64, message = "密码不能超过64个字符")
    private String password;
}
