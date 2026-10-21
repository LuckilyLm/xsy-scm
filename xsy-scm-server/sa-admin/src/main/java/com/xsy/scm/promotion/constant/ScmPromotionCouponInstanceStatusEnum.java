package com.xsy.scm.promotion.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 客户券实例的权益状态，与券模板启停状态分开。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPromotionCouponInstanceStatusEnum {

    /** 可用：尚未被订单占用。 */
    AVAILABLE("可用"),

    /** 已占用：被待支付订单锁定。 */
    RESERVED("已占用"),

    /** 已使用：随订单支付生效。 */
    USED("已使用"),

    /** 已释放：订单取消后归还。 */
    RELEASED("已释放");

    private final String desc;
}
