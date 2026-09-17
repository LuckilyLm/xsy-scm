package com.xsy.scm.admin.module.business.order.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单结算方式
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum SettleTypeEnum implements BaseEnum {

    /**
     * 1 账期支付
     */
    CREDIT(1, "账期支付"),

    /**
     * 2 货到付款
     */
    COD(2, "货到付款"),

    /**
     * 3 在线支付
     */
    ONLINE(3, "在线支付"),

    /**
     * 4 余额充值
     */
    BALANCE(4, "余额充值"),

    ;

    private final Integer value;

    private final String desc;
}
