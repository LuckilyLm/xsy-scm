package com.xsy.scm.common.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 客户账期：按金额或按周期结算，也可以不设。两种方式只能选一种，校验器和数据库都会拦。
 *
 * <p>
 * {@code desc} 供页面与导出使用，数据库仍存枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmCreditPeriodTypeEnum {

    /** 按累计金额触发结算。 */
    BY_AMOUNT("按金额"),

    /** 按时间周期结算。 */
    BY_TIME("按周期");

    private final String desc;
}
