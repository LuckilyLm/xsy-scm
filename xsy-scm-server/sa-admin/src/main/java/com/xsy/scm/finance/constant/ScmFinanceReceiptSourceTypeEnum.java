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

    /** V131 历史来源：{@code source_id = xsy_sales_order.id}，保留旧事实不改写。 */
    ORDER_COD_COLLECTION("货到付款收款"),

    /** 分笔 COD 来源：{@code source_id = xsy_idempotency_record.id}，订单由 cod_order_id 指明。 */
    ORDER_COD_INSTALLMENT("货到付款分笔收款");

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
