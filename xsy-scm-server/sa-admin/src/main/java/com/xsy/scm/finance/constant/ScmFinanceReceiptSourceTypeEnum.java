package com.xsy.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 收款事实的系统来源类型（{@code xsy_finance_receipt.source_type}）。
 *
 * <p>
 * 与付款的 {@link ScmFinancePaymentSourceTypeEnum} <b>刻意分开</b>：收款与付款的来源是两组不同的事实 （「这笔钱从哪来」vs「这笔钱付给谁」），共用一个枚举只会让两边的值域互相牵制。
 *
 * <p>
 * 用枚举而不是「常量类 + 字符串」：字符串字面量会与其它域已声明的同名值撞上质量门禁 （{@code magic-string-domain-literal}），而枚举常量名不会。
 */
@Getter
@RequiredArgsConstructor
public enum ScmFinanceReceiptSourceTypeEnum {

    /** 来源是支付域的交易事实：{@code source_id = xsy_payment_transaction.id}（ADM-12）。 */
    PAYMENT_TRANSACTION("支付交易"),

    /**
     * 来源是货到付款的订单：{@code source_id = xsy_sales_order.id}。
     *
     * <p>
     * 唯一索引 {@code uk_finance_receipt_source_active} 因此保证「一张 COD 订单最多一笔收款」—— 重复登记由库直接拦下，而不是靠服务层「先查后写」。
     */
    ORDER_COD_COLLECTION("货到付款收款");

    private final String desc;

    public static boolean isSupported(String value) {
        for (ScmFinanceReceiptSourceTypeEnum item : values()) {
            if (item.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
