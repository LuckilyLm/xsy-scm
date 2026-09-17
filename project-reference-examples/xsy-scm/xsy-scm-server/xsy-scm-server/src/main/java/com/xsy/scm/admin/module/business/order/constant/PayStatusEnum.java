package com.xsy.scm.admin.module.business.order.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单支付状态
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum PayStatusEnum implements BaseEnum {

    /**
     * 1 未付
     */
    UNPAID(1, "未付"),

    /**
     * 2 部分支付
     */
    PARTIAL(2, "部分支付"),

    /**
     * 3 已付
     */
    PAID(3, "已付"),

    ;

    private final Integer value;

    private final String desc;
}
