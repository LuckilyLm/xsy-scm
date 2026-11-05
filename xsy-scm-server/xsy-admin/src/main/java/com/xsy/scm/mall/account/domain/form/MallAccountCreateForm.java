package com.xsy.scm.mall.account.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MallAccountCreateForm {
    @NotNull(message = "客户不能为空")
    private Long customerId;
    @NotBlank(message = "登录手机号不能为空")
    @Size(max = 32, message = "登录手机号不能超过32个字符")
    private String loginPhone;
    @NotBlank(message = "临时密码不能为空")
    @Size(min = 8, max = 64, message = "临时密码必须为8至64个字符")
    private String temporaryPassword;
}
