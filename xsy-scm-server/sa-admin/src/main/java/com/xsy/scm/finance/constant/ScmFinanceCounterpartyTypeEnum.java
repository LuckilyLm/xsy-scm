package com.xsy.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 付款对象分供应商与客户退款两类。客户付款必须校验客户业务员的数据范围；供应商历史欠款仍可按统一供应商主档处理。 */
@Getter
@RequiredArgsConstructor
public enum ScmFinanceCounterpartyTypeEnum {

    /**
     * 供应商：对应 {@code xsy_finance_payable} 的核销与预付。
     */
    SUPPLIER("供应商"),

    /**
     * 客户：对应退款付款（{@code source_type = ORDER_REFUND}）。
     */
    CUSTOMER("客户");

    private final String desc;
}
