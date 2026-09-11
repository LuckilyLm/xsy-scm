package com.xianshuyuan.scm.mall.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 微信登录预留。当前服务端不实现 code 交换，接口保留以固定前后端契约。
 */
public record MallWechatLoginRequest(@NotBlank(message = "微信登录凭证不能为空") String code) {
}
