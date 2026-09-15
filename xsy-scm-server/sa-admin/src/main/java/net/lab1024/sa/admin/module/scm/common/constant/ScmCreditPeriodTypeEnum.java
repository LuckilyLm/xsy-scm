package net.lab1024.sa.admin.module.scm.common.constant;

/**
 * 账期类型。
 *
 * <p>{@code BY_AMOUNT} = 按金额：达到 {@code credit_amount_threshold} 后触发结算；
 * {@code BY_TIME} = 按时间：以 {@code credit_period_value} + {@code credit_period_unit} 计账期。
 *
 * <p>两者互斥，且都可以不设置（{@code credit_period_type} 为 {@code NULL}）。互斥性由
 * {@code customer} 表的 {@code ck_customer_credit_period} 约束与
 * {@code CustomerValidator} 双重保证。
 */
public enum ScmCreditPeriodTypeEnum {
    BY_AMOUNT,
    BY_TIME
}
