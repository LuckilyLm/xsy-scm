package com.xsy.scm.admin.module.business.customer.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 客户类型
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum CustomerTypeEnum implements BaseEnum {

    /**
     * 1 企业
     */
    ENTERPRISE(1, "企业"),

    /**
     * 2 个人
     */
    PERSONAL(2, "个人"),

    /**
     * 3 集团
     */
    GROUP(3, "集团"),

    ;

    private final Integer value;

    private final String desc;
}
