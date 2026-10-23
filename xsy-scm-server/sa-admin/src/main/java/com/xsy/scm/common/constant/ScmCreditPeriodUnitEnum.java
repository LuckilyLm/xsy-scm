package com.xsy.scm.common.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 账期时间单位（仅 {@link ScmCreditPeriodTypeEnum#BY_TIME} 时有值）。
 *
 * <p>
 * {@code MONTH} 时可指定固定结算日 1–28：每个月都有这一天，2 月也不会算不出账期。
 *
 * <p>
 * {@code desc} 供页面与导出使用，数据库仍存枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmCreditPeriodUnitEnum {

    /** 按天计算账期。 */
    DAY("天"),

    /** 按月计算账期。 */
    MONTH("月");

    private final String desc;
}
