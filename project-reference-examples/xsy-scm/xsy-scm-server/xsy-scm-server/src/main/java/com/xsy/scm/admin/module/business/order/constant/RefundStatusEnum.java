package com.xsy.scm.admin.module.business.order.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 退款状态
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum RefundStatusEnum implements BaseEnum {

    /**
     * 1 待审核
     */
    PENDING(1, "待审核"),

    /**
     * 2 已通过
     */
    APPROVED(2, "已通过"),

    /**
     * 3 已退款
     */
    REFUNDED(3, "已退款"),

    /**
     * 4 已驳回
     */
    REJECTED(4, "已驳回"),

    ;

    private final Integer value;

    private final String desc;
}
