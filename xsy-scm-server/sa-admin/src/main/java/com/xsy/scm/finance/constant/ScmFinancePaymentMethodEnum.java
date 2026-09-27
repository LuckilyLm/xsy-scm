package com.xsy.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 收付款方式：固定三值，收款与付款**共用同一套枚举**。
 *
 * <p>
 * 值由 Java enum 和数据库 CHECK 固定，**不引入 SCM {@code t_dict}**： 字典表适合运营可维护的取值，而方式一旦能被随意增删，历史收付款的方式语义就会漂移， 且有限的支付方式也无法在数据库中约束。
 *
 * <p>
 * 取值限定为 CASH、BANK_TRANSFER、OTHER。扩展方式时，枚举与 {@code ck_finance_receipt_method}、{@code ck_finance_payment_method} 必须同步变更。
 */
@Getter
@RequiredArgsConstructor
public enum ScmFinancePaymentMethodEnum {

    /**
     * 现金。
     */
    CASH(
            "现金"),

    /**
     * 银行转账。
     */
    BANK_TRANSFER(
            "银行转账"),

    /**
     * 其它；具体渠道写在 {@code external_reference} 或 {@code remark} 里。
     */
    OTHER(
            "其它");

    private final String desc;
}
