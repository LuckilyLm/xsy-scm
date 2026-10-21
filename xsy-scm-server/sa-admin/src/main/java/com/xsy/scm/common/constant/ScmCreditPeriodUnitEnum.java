package com.xsy.scm.common.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 账期时间单位（仅当 {@link ScmCreditPeriodTypeEnum#BY_TIME} 时有值）。
 *
 * <p>
 * 单位为 {@code MONTH} 时允许额外指定固定结算日（1–28）。上限取 28 而非 31，是为了让每个自然月都存在该日期，避免 2 月无 29/30/31 日导致账期不可计算。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
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
