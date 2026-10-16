package com.xsy.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 收款方式与付款方式分开维护；线上收款只属于收款方式，不应混入付款枚举。 */
@Getter
@RequiredArgsConstructor
public enum ScmFinanceReceiptMethodEnum {

    /** 现金。 */
    CASH("现金"),

    /** 银行转账。 */
    BANK_TRANSFER("银行转账"),

    /** 在线支付（经支付渠道收取，来源见 {@code xsy_finance_receipt.source_type}）。 */
    ONLINE_PAYMENT("在线支付"),

    /** 其它；具体渠道写在 {@code external_reference} 或 {@code remark} 里。 */
    OTHER("其它");

    private final String desc;

    public static boolean isSupported(String value) {
        for (ScmFinanceReceiptMethodEnum item : values()) {
            if (item.name().equals(value)) {
                return true;
            }
        }
        return false;
    }

    public static ScmFinanceReceiptMethodEnum of(String value) {
        for (ScmFinanceReceiptMethodEnum item : values()) {
            if (item.name().equals(value)) {
                return item;
            }
        }
        return null;
    }
}
