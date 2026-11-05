package com.xsy.scm.mall.cart.constant;

/**
 * 购物车行的可结算状态；库存量只用于展示，结算与下单时由服务端重验可售、报价与数量规则。
 */
public enum MallCartAvailabilityEnum {
    /**
     * 可见且有客户价，可参与结算
     */
    AVAILABLE,
    /**
     * 可见但无客户价，需询价
     */
    NO_PRICE,
    /**
     * 起订量或递增量已变化，购物车中的历史数量需调整
     */
    QUANTITY_INVALID,
    /**
     * 已下架、分类停用或对当前客户不可见
     */
    UNAVAILABLE
}
