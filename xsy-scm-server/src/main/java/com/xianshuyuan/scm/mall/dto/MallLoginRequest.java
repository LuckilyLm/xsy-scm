package com.xianshuyuan.scm.mall.dto;

import jakarta.validation.constraints.NotBlank;

public record MallLoginRequest(
        @NotBlank(message = "请输入账号") String username,
        @NotBlank(message = "请输入密码") String password
) {
}
