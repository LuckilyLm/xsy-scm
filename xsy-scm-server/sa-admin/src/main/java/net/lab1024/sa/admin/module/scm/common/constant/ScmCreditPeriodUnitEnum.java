package net.lab1024.sa.admin.module.scm.common.constant;

/**
 * 账期时间单位（仅当 {@link ScmCreditPeriodTypeEnum#BY_TIME} 时有值）。
 *
 * <p>单位为 {@code MONTH} 时允许额外指定固定结算日（1–28）。上限取 28 而非 31，
 * 是为了让每个自然月都存在该日期，避免 2 月无 29/30/31 日导致账期不可计算。
 */
public enum ScmCreditPeriodUnitEnum {
    DAY,
    MONTH
}
