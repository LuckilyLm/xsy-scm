package com.xsy.scm.mall.account.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 商城客户账号状态。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum MallAccountStatusEnum {

    /** 启用。 */
    ENABLED("启用"),

    /** 停用：不能登录商城，历史订单保留。 */
    DISABLED("停用");

    private final String desc;
}
