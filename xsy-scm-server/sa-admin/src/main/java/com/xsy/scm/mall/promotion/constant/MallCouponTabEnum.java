package com.xsy.scm.mall.promotion.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 商城「我的优惠券」页签。
 *
 * <p>
 * {@code desc} 供页面与导出使用，保存仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum MallCouponTabEnum {

    /**
     * 可用。
     */
    AVAILABLE("可用"),

    /**
     * 已使用。
     */
    USED("已使用"),

    /**
     * 已失效：过期或被作废。
     */
    INVALID("已失效");

    private final String desc;
}
