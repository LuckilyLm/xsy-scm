package net.lab1024.sa.admin.module.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 应付明细的来源类型，{@code source_id} 的空 / 非空由
 * {@code ck_finance_payable_item_source_pairing} 在库级配对。
 */
@Getter
@RequiredArgsConstructor
public enum ScmFinancePayableItemSourceTypeEnum {

    /**
     * 正常明细：{@code source_id} = {@code purchase_receipt_item.id}。
     */
    PURCHASE_RECEIPT_ITEM("采购收货明细"),

    /**
     * 手工红字明细：{@code source_id} 必须为 {@code null}，行级追溯靠
     * {@code purchase_order_item_id} 与单头的 {@code original_payable_id}。
     */
    MANUAL("手工登记");

    private final String desc;
}
