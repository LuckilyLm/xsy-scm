package com.xsy.scm.payment.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 支付业务来源类型由支付域维护，与订单和钱包的类型分开。来源码必须符合数据库约束，不能自由填写。 */
@Getter
@RequiredArgsConstructor
public enum ScmPaymentSourceTypeEnum {

    /** 支付意图的来源：销售订单。 */
    SALES_ORDER("销售订单"),

    /** 退款的来源：售后退款单。 */
    ORDER_REFUND("售后退款单"),

    /**
     * 支付意图的来源：客户余额充值（ADM-12）。
     *
     * <p>
     * {@code source_id} 指向 {@code xsy_customer_balance_recharge.id} —— <b>不是</b>客户、结算主体或 余额账户 id：同一集团连续充 100、200、500
     * 时那些 ID 全都一样，回答不了 「这笔 PaymentIntent 是哪一次充值」。
     */
    BALANCE_RECHARGE("余额充值");

    private final String desc;

    public static boolean isSupported(String value) {
        for (ScmPaymentSourceTypeEnum item : values()) {
            if (item.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
