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

    /** 满减：{@code {thresholdAmount, reduceAmount}}。 */
    FULL_REDUCE("满减"),

    /** 折扣：{@code {discountRate}}，例如 0.95 表示 95 折。 */
    DISCOUNT("折扣"),

    /** 满赠：{@code {thresholdAmount, giftSkuId, giftQuantity}}；赠品不产生订单优惠金额。 */
    FULL_GIFT("满赠"),

    /**
     * 限时特价：{@code {skuId, specialPrice}}。
     *
     * <p>
     * 它作用在<b>基础价之后</b>：让利 = 行基础金额 − 数量 × 特价，且只在特价低于该行单价时成立 （特价只能往下压，不能抬高）。让利按<b>行</b>归集，不像满减/折扣那样按金额比例分摊 —— 特价是针对某个 SKU
     * 的，摊到别的行上会让退款反向错行。
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
