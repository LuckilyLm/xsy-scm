package com.xsy.scm.mall.home.constant;

import com.xsy.scm.common.error.ScmErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MallHomeErrorCode implements ScmErrorCode {
    INVALID_LAYOUT(41400, "商城首页配置不正确"),
    INVALID_LINK(41401, "商城首页跳转目标不正确或不在允许范围内"),
    INVALID_IMAGE(41402, "商城首页图片必须是已上传的公开图片"),
    RELEASE_NOT_FOUND(40495, "商城首页发布版本不存在");

    private final int code;
    private final String msg;
}
