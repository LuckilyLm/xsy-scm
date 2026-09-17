package com.xsy.scm.admin.module.business.order.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 退款类型
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum RefundTypeEnum implements BaseEnum {

    /**
     * 1 仅退款
     */
    REFUND_ONLY(1, "仅退款"),

    /**
     * 2 退货退款
     */
    RETURN_REFUND(2, "退货退款"),

    ;

    private final Integer value;

    private final String desc;
}
