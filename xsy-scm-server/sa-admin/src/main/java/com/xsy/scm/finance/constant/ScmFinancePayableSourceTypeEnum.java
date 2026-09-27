package com.xsy.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 应付单头的来源类型，与 {@link ScmFinanceEntryTypeEnum} 由 {@code ck_finance_payable_source_pairing} 在库级配对。
 *
 * <p>
 * {@code PURCHASE_RECEIPT} 表示收货派生的正常应付；{@code MANUAL} 表示没有业务来源单据的手工红字应付。
 *
 * <p>
 * {@code MANUAL} 行的 {@code source_id} 必须为 {@code NULL}，这让它落在 {@code uk_finance_payable_source_active} 的
 * {@code source_id IS NOT NULL} 谓词之外； 其防重由金额上限和请求级幂等键承担。
 */
@Getter
@RequiredArgsConstructor
public enum ScmFinancePayableSourceTypeEnum {

    /**
     * 正常应付：{@code source_id} = {@code purchase_receipt.id}。
     */
    PURCHASE_RECEIPT("采购收货单"),

    /**
     * 手工红字应付：{@code source_id} 必须为 {@code null}，{@code original_payable_id} 与 {@code reason} 必填。
     */
    MANUAL("手工登记");

    private final String desc;
}
