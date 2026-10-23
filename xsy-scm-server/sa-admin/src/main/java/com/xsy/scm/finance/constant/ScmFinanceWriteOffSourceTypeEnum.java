package com.xsy.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 核销行的资金来源侧类型，与 {@link ScmFinanceWriteOffTargetTypeEnum} 由 {@code ck_finance_write_off_pairing}
 * 在数据库层配对：收款只能核应收，付款只能核应付。
 *
 * <p>
 * 这条约束挡的是「用一笔收款去核应付」这类跨方向核销：页面上选不出来，但没有数据库约束时，绕过服务层的写入会造出一笔方向错乱、金额却看似正常的核销。
 */
@Getter
@RequiredArgsConstructor
public enum ScmFinanceWriteOffSourceTypeEnum {

    /**
     * 收款：{@code source_id} = {@code xsy_finance_receipt.id}，只能核 {@code RECEIVABLE}。
     */
    RECEIPT("收款"),

    /**
     * 付款：{@code source_id} = {@code xsy_finance_payment.id}，只能核 {@code PAYABLE}。
     */
    PAYMENT("付款"),

    BALANCE_MOVEMENT("余额消费");

    private final String desc;
}
