package com.xsy.scm.mall.account.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 微信手机号授权的一次性凭证；手机号必须由服务端向微信换取。 */
@Data
public class MallWechatPhoneLoginForm {
    @NotBlank(message = "请先授权微信手机号")
    @Size(max = 512, message = "微信授权信息无效，请重新授权")
    private String code;
}
