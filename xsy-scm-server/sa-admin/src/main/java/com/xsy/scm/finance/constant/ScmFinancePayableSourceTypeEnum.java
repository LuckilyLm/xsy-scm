package com.xsy.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 收货确认生成正常应付，手工红字应付没有来源单据 ID。两类来源分别按唯一索引、金额校验及幂等键防重。
 */
@Getter
@RequiredArgsConstructor
public enum ScmFinancePayableSourceTypeEnum {

    /**
     * 正常应付：{@code source_id} = {@code xsy_purchase_receipt.id}。
     */
    PURCHASE_RECEIPT("采购收货单"),
    PURCHASE_ACCOUNT("采购确认"),

    /**
     * 手工红字应付：{@code source_id} 必须为 {@code null}，{@code original_payable_id} 与 {@code reason} 必填。
     */
    MANUAL("手工登记"),
    PURCHASE_COST_ADJUSTMENT("采购价差更正");

    private final String desc;
}
