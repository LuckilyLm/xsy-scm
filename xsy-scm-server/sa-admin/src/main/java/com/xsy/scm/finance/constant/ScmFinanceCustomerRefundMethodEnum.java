package com.xsy.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * **客户退款**的付款方式：与供应商付款的 {@link ScmFinancePaymentMethodEnum} 分开。
 *
 * <p>
 * <b>为什么不能直接扩那个枚举</b>：它是供应商付款与客户退款共用的。加 {@code ONLINE_PAYMENT}
 * 会让供应商付款入口也拿到这个值，而库上的 {@code ck_finance_payment_method} 是按对手方约束的
 * —— 供应商付款带 ONLINE_PAYMENT 会被数据库拒绝。让前端能选、后端必然失败，是最差的一种「支持」。
 *
 * <p>
 * 数据库侧同义：{@code ck_finance_payment_method} 对 {@code CUSTOMER} 放行这四个值，
 * 对 {@code SUPPLIER} 仍然只放行前三个中的 CASH / BANK_TRANSFER / OTHER。
 */
@Getter
@RequiredArgsConstructor
public enum ScmFinanceCustomerRefundMethodEnum {

    /** 现金退还。 */
    CASH("现金"),

    /** 银行转账退还。 */
    BANK_TRANSFER("银行转账"),

    /** 在线支付原路退回（系统生成的退款付款固定用它）。 */
    ONLINE_PAYMENT("在线支付"),

    /** 其它。 */
    OTHER("其它");

    private final String desc;

    public static boolean isSupported(String value) {
        for (ScmFinanceCustomerRefundMethodEnum item : values()) {
            if (item.name().equals(value)) {
                return true;
            }
        }
        return false;
    }

    public static ScmFinanceCustomerRefundMethodEnum of(String value) {
        for (ScmFinanceCustomerRefundMethodEnum item : values()) {
            if (item.name().equals(value)) {
                return item;
            }
        }
        return null;
    }
}
