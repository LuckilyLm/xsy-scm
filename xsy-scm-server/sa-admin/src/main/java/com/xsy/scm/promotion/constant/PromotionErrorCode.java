package com.xsy.scm.promotion.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.error.ScmErrorCode;

/**
 * 营销域使用 41320–41339 错误码（41301–41310 属打印域）。
 */
@Getter
@RequiredArgsConstructor
public enum PromotionErrorCode implements ScmErrorCode {

    ACTIVITY_NOT_FOUND(41320, "营销活动不存在或已被删除"),

    ACTIVITY_CODE_DUPLICATED(41321, "活动编码已存在，请更换"),

    ACTIVITY_STATE_INVALID(41322, "活动当前状态不允许此操作"),

    ACTIVITY_WINDOW_INVALID(41323, "生效时间必须早于失效时间"),

    RULE_INVALID(41324, "活动规则不合法：请检查门槛、减免或折扣率"),

    COUPON_NOT_FOUND(41325, "优惠券不存在或已被删除"),

    COUPON_CODE_DUPLICATED(41326, "券编码已存在，请更换"),

    COUPON_STATE_INVALID(41327, "优惠券当前状态不允许此操作"),

    COUPON_INSTANCE_NOT_FOUND(41328, "客户券不存在"),

    /** 该客户在这张券模板下没有可用的券。 */
    COUPON_INSTANCE_UNAVAILABLE(41329, "该客户没有可用的券，请先发券或换一张券"),

    COUPON_INSTANCE_STATE_INVALID(41330, "客户券当前状态不允许此操作"),

    /** 券的优惠类型是折扣率时，取值必须在 (0, 1) 之间。 */
    COUPON_RATE_INVALID(41331, "折扣率必须在0与1之间"),

    /** 门槛未达到：券与活动都要按门槛判定，不达门槛不能应用。 */
    THRESHOLD_NOT_REACHED(41332, "订单金额未达到优惠门槛"),

    DISCOUNT_ALREADY_FROZEN(41333, "该订单的优惠已冻结，不能重复计算");

    private final int code;
    private final String msg;
}
