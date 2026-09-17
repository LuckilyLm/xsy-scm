package com.xsy.scm.admin.module.business.customer.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 客户结算方式
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum SettleModeEnum implements BaseEnum {

    /**
     * 1 独立结算
     */
    INDEPENDENT(1, "独立结算"),

    /**
     * 2 集团统一结算
     */
    GROUP(2, "集团统一结算"),

    ;

    private final Integer value;

    private final String desc;
}
