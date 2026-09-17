package com.xsy.scm.admin.module.business.customer.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 账期单位
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum PeriodUnitEnum implements BaseEnum {

    /**
     * 1 天
     */
    DAY(1, "天"),

    /**
     * 2 月
     */
    MONTH(2, "月"),

    ;

    private final Integer value;

    private final String desc;
}
