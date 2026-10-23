package com.xsy.scm.promotion.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 券的优惠类型。金额与折扣率不合并成一列：二者在展示、对账和门槛判定上完全不同，用「一列 + 一个类型标记」才不会读错。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPromotionCouponDiscountTypeEnum {

    /**
     * 减免金额：{@code discountValue} 是金额。
     */
    AMOUNT("满减券"),

    /**
     * 折扣率：{@code discountValue} 是 0~1 之间的比例，例如 0.95。
     */
    RATE("折扣券");

    private final String desc;

    public static boolean isSupported(String value) {
        for (ScmPromotionCouponDiscountTypeEnum item : values()) {
            if (item.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
