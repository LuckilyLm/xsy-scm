package com.xsy.scm.mall.account.constant;

/** 认证来源进入会话身份，防止密码会话继承微信手机号验证的改密豁免。 */
public enum MallLoginMethodEnum {
    /** 手机号密码认证。 */
    PASSWORD,
    /** 微信官方手机号授权认证。 */
    WECHAT_PHONE
}
