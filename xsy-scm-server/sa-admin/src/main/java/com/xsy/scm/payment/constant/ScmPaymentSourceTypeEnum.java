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

    /** 余额充值支付的来源 ID 指向具体充值单，不是客户或钱包账户 ID。每次充值都必须有独立来源。 */
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
