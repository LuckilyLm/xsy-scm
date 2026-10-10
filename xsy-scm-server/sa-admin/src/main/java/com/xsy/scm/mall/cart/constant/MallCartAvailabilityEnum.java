package com.xsy.scm.mall.cart.constant;

/** 购物车行的可结算状态；库存充足性在结算与下单时由服务端重验，不在此固化。 */
public enum MallCartAvailabilityEnum {
    /** 可见且有客户价，可参与结算 */
    AVAILABLE,
    /** 可见但无客户价，需询价 */
    NO_PRICE,
    /** 已下架、分类停用或对当前客户不可见 */
    UNAVAILABLE
}
