package com.xsy.scm.balance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 钱包流水的来源类型必须符合数据库白名单。来源类型不能随意填写，否则会绕过同一业务来源的防重约束。 */
@Getter
@RequiredArgsConstructor
public enum ScmBalanceSourceTypeEnum {

    /** 来源是支付域的交易事实：{@code source_id = xsy_payment_transaction.id}（充值用）。 */
    PAYMENT_TRANSACTION("支付交易"),

    PAYMENT_INTENT("订单余额支付"),

    /** 来源是售后退款单：{@code source_id = xsy_order_refund.id}（旧纯余额返还用）。 */
    ORDER_REFUND("售后退款单"),

    /** 来源是退款资金分摊行：{@code source_id = xsy_refund_funding_allocation.id}。 */
    REFUND_FUNDING_ALLOCATION("退款资金分摊");

    private final String desc;

    public static boolean isSupported(String value) {
        for (ScmBalanceSourceTypeEnum item : values()) {
            if (item.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
