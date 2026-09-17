package com.xsy.scm.admin.module.business.customer.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 客户账期状态
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum PeriodStatusEnum implements BaseEnum {

    /**
     * 1 生效
     */
    EFFECTIVE(1, "生效"),

    /**
     * 2 暂停
     */
    SUSPENDED(2, "暂停"),

    ;

    private final Integer value;

    private final String desc;
}
