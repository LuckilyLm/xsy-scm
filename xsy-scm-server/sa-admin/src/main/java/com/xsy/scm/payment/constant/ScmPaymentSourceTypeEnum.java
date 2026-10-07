package com.xsy.scm.payment.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 支付域自己的<b>业务来源类型</b>（{@code payment_intent.source_type} / {@code payment_refund.source_type}）。
 *
 * <p>
 * 与财务域的 {@code ScmFinancePaymentSourceTypeEnum} 值相同但<b>归属不同</b>：那个枚举描述的是 「财务付款事实的来源」，这里描述的是「支付意图 / 支付退款的业务来源」。两边值相同由各自的
 * 库级 CHECK 分别固定 —— 是断言，不是靠一处定义顺带覆盖另一处。
 *
 * <p>
 * 用枚举而不是「常量类 + 字符串」：字符串字面量会与其它域已声明的同名值撞上质量门禁 （{@code magic-string-domain-literal}），而枚举常量名不会。
 */
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
     * {@code source_id} 指向 {@code customer_balance_recharge.id} —— <b>不是</b>客户、结算主体或 余额账户 id：同一集团连续充 100、200、500 时那些 ID
     * 全都一样，回答不了 「这笔 PaymentIntent 是哪一次充值」。
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
