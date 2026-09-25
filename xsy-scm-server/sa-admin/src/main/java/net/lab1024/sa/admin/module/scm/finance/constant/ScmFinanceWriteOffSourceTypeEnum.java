package net.lab1024.sa.admin.module.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 核销行的资金来源侧类型，与 {@link ScmFinanceWriteOffTargetTypeEnum} 由
 * {@code ck_finance_write_off_pairing} 在库级配对：收款只能核应收，付款只能核应付。
 *
 * <p>这条配对约束挡的是「用一笔收款去核应付」这类跨侧分配 —— 它在页面上不可能选出来，
 * 但没有库级约束时，任何绕过服务层的写入都会造出一笔方向错乱、金额却看起来正常的核销。
 */
@Getter
@RequiredArgsConstructor
public enum ScmFinanceWriteOffSourceTypeEnum {

    /**
     * 收款：{@code source_id} = {@code finance_receipt.id}，只能核 {@code RECEIVABLE}。
     */
    RECEIPT("收款"),

    /**
     * 付款：{@code source_id} = {@code finance_payment.id}，只能核 {@code PAYABLE}。
     */
    PAYMENT("付款");

    private final String desc;
}
