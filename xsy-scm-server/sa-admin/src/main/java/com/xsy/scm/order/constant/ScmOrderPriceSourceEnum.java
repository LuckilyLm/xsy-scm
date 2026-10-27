package com.xsy.scm.order.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 订单行成交价的来源，用于解释这个价格是怎么定出来的。
 *
 * <p>
 * {@code desc} 供页面与导出使用，保存仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmOrderPriceSourceEnum {

    /**
     * 客户协议价。
     */
    AGREEMENT("协议价"),

    /**
     * 客户分组价。
     */
    CUSTOMER_GROUP("客户分组价"),

    /**
     * 客户类型价。
     */
    CUSTOMER_TYPE("客户类型价"),

    /**
     * 限时价格。
     */
    TIME_PRICE("时价"),

    /**
     * 商品市场价。
     */
    MARKET("市场价"),

    /**
     * 人工改价。
     */
    OVERRIDE("人工改价");

    private final String desc;
}
