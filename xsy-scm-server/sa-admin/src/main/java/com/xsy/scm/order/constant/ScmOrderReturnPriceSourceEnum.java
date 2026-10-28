package com.xsy.scm.order.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 独立退货行的取价来源。
 *
 * <p>
 * 协议价、分组价、类型价、时价、市场价这五个值与 {@link com.xsy.scm.pricing.constant.ScmPriceSourceEnum} 同名， 保存枚举名以便与订单定价互相核对； 另外两个值只属于退货：
 */
@Getter
@RequiredArgsConstructor
public enum ScmOrderReturnPriceSourceEnum {

    /**
     * 该客户该商品该单位最近一次已确认订单的成交价。
     */
    RECENT_TRANSACTION("最近成交价"),

    /**
     * 取价链（协议价 → 分组价 → 类型价 → 时价 → 市场价）给出的价格。
     */
    AGREEMENT("协议价"),
    CUSTOMER_GROUP("客户分组价"),
    CUSTOMER_TYPE("客户类型价"),
    TIME_PRICE("时价"),
    MARKET("市场价"),

    /**
     * 系统无可用价格或人工改价，必须同时填写原因。
     */
    MANUAL("人工填价");

    private final String desc;
}
