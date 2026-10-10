package com.xsy.scm.mall.account.constant;

import com.xsy.scm.common.error.ScmErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MallErrorCode implements ScmErrorCode {
    ACCOUNT_NOT_FOUND(40480, "商城账号不存在"),
    LOGIN_FAILED(40080, "手机号或密码错误"),
    ACCOUNT_DISABLED(40980, "商城账号已禁用"),
    PHONE_DUPLICATE(40981, "登录手机号已被使用"),
    CUSTOMER_ACCOUNT_DUPLICATE(40982, "该客户已开通商城账号"),
    PASSWORD_CHANGE_REQUIRED(40983, "请先修改临时密码"),
    PASSWORD_INVALID(40081, "密码必须为8至64个字符"),
    CATALOG_PRODUCT_NOT_FOUND(40481, "商品不存在");

    private final int code;
    private final String msg;
}
