package com.xsy.scm.common.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 客户账期可以按金额阈值结算，或按时间周期结算，也可以不设置。两种模式互斥，由校验器和数据库共同保证。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
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
