package com.xsy.scm.promotion.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 活动类型。
 *
 * <p>
 * 只收录<b>真正能算</b>的类型。限时特价已由负责人确认（2026-10-03）：它<b>不改基础定价链</b> （协议价 → 客户类型价 → 市场价），而是作为 Promotion 在基础价之后作用 （基础价 → 限时特价 →
 * 满减/折扣 → 优惠券），因此可以收录。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPromotionActivityTypeEnum {

    /**
     * 满减：{@code {thresholdAmount, reduceAmount}}。
     */
    FULL_REDUCE("满减"),

    /**
     * 折扣：{@code {discountRate}}，例如 0.95 表示 95 折。
     */
    DISCOUNT("折扣"),

    /**
     * 满赠：{@code {thresholdAmount, giftSkuId, giftQuantity}}；赠品不产生订单优惠金额。
     */
    FULL_GIFT("满赠"),

    /**
     * 特价先于其他优惠计算，只能降价不能抬价；特价优惠归属命中的商品行，退款时按原行反向处理。
     */
    SPECIAL_PRICE("限时特价");

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
