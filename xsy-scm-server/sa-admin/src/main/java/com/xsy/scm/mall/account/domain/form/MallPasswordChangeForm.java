package com.xsy.scm.mall.account.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MallPasswordChangeForm {
    @NotBlank(message = "原密码不能为空")
    private String oldPassword;
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 64, message = "新密码必须为8至64个字符")
    private String newPassword;
}
