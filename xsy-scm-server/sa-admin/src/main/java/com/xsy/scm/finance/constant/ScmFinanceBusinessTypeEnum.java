package com.xsy.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 财务操作日志的业务对象类型，对应 {@code xsy_finance_operation_log.business_type} 的 {@code ck_finance_operation_log_business_type}
 * CHECK 约束。
 *
 * <p>
 * 日志按「对象类型 + 对象 id」定位，不存单号：单号读时从对应业务表取，存两份容易不一致。
 */
@Getter
@RequiredArgsConstructor
public enum ScmFinanceBusinessTypeEnum {

    /**
     * 应收单（{@code xsy_finance_receivable}）。
     */
    RECEIVABLE("应收"),

    /**
     * 应付单（{@code xsy_finance_payable}）。
     */
    PAYABLE("应付"),

    /**
     * 收款单（{@code xsy_finance_receipt}）。
     */
    RECEIPT("收款"),

    /**
     * 付款单（{@code xsy_finance_payment}）。
     */
    PAYMENT("付款"),

    /**
     * 核销行（{@code xsy_finance_write_off}）。
     */
    WRITE_OFF("核销"),

    /**
     * 抹零事实（{@code xsy_finance_waiver}）。
     */
    WAIVER("抹零");

    private final String desc;
}
