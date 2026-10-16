package com.xsy.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 付款方式与收款方式使用不同枚举；线上收款、钱包扣款不属于供应商付款方式，不能把两边的资金方向混在一个列表。 */
@Getter
@RequiredArgsConstructor
public enum ScmFinancePaymentMethodEnum {

    /**
     * 现金。
     */
    CASH("现金"),

    /**
     * 银行转账。
     */
    BANK_TRANSFER("银行转账"),

    /**
     * 其它；具体渠道写在 {@code external_reference} 或 {@code remark} 里。
     */
    OTHER("其它");

    private final String desc;
}
