package com.xsy.scm.promotion.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 活动类型。
 *
 * <p>
 * 只收录本阶段**真正能算**的类型。限时特价是「改基础价」而不是「订单级优惠」，与
 * 「基础定价顺序不变、活动价在基础价之后计算」这条边界冲突，需要单独裁决，因此不先占位。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPromotionActivityTypeEnum {

    /** 满减：{@code {thresholdAmount, reduceAmount}}。 */
    FULL_REDUCE("满减"),

    /** 折扣：{@code {discountRate}}，例如 0.95 表示 95 折。 */
    DISCOUNT("折扣"),

    /** 满赠：{@code {thresholdAmount, giftSkuId, giftQuantity}}；赠品不产生订单优惠金额。 */
    FULL_GIFT("满赠");

    private final String desc;

    public static boolean isSupported(String value) {
        for (ScmPromotionActivityTypeEnum item : values()) {
            if (item.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
