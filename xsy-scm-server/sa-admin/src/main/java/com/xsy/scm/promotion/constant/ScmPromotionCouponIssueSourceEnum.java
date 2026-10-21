package com.xsy.scm.promotion.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 优惠券实例的产生来源；自助领取额度只统计商城领取事实。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPromotionCouponIssueSourceEnum {

    /** 后台发放。 */
    ADMIN_ISSUE("后台发放"),

    /** 商城自助领取。 */
    MALL_CLAIM("商城领取");

    private final String desc;
}
