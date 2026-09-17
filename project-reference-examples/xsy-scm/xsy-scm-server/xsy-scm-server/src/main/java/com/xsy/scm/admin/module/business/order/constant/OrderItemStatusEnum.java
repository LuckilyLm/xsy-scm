package com.xsy.scm.admin.module.business.order.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单明细状态
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum OrderItemStatusEnum implements BaseEnum {

    /**
     * 1 正常
     */
    NORMAL(1, "正常"),

    /**
     * 2 已退款
     */
    REFUNDED(2, "已退款"),

    /**
     * 3 已退货
     */
    RETURNED(3, "已退货"),

    ;

    private final Integer value;

    private final String desc;
}
