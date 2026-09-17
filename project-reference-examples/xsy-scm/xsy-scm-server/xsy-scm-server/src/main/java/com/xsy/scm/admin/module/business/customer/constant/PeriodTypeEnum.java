package com.xsy.scm.admin.module.business.customer.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 客户账期类型
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum PeriodTypeEnum implements BaseEnum {

    /**
     * 1 按金额
     */
    BY_AMOUNT(1, "按金额"),

    /**
     * 2 按时间
     */
    BY_TIME(2, "按时间"),

    ;

    private final Integer value;

    private final String desc;
}
