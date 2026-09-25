package net.lab1024.sa.admin.module.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 财务操作日志的业务对象类型，对应 {@code finance_operation_log.business_type} 的
 * {@code ck_finance_operation_log_business_type} 白名单。
 *
 * <p>日志按「对象类型 + 对象 id」定位，不存对象单号：单号是可读的派生展示值，
 * 读时 join 对应事实表取得，落两份就会漂移。
 */
@Getter
@RequiredArgsConstructor
public enum ScmFinanceBusinessTypeEnum {

    /**
     * 应收单（{@code finance_receivable}）。
     */
    RECEIVABLE("应收"),

    /**
     * 应付单（{@code finance_payable}）。
     */
    PAYABLE("应付"),

    /**
     * 收款单（{@code finance_receipt}）。
     */
    RECEIPT("收款"),

    /**
     * 付款单（{@code finance_payment}）。
     */
    PAYMENT("付款"),

    /**
     * 核销行（{@code finance_write_off}）。
     */
    WRITE_OFF("核销");

    private final String desc;
}
