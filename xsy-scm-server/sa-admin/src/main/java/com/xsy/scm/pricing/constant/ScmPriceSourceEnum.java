package com.xsy.scm.pricing.constant;

public enum ScmPriceSourceEnum {
    AGREEMENT,
    CUSTOMER_TYPE,
    /** 商品规格级、按时间段生效的临时价格；压在客户类型价之下、市场价之上。 */
    TIME_PRICE,
    MARKET
}
