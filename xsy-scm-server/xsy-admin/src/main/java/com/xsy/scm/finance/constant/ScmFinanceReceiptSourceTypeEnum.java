package com.xsy.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 系统收款的来源类型与付款来源独立维护。来源类型须与财务数据和数据库 CHECK 约束匹配，不能随意复用另一业务的枚举。
 */
@Getter
@RequiredArgsConstructor
public enum ScmFinanceReceiptSourceTypeEnum {

    /**
     * 来源是支付模块的交易记录：{@code source_id = xsy_payment_transaction.id}。
     */
    PAYMENT_TRANSACTION("支付交易"),

    /**
     * 历史来源：{@code source_id = xsy_sales_order.id}，保留旧记录不改写。
     */
    ORDER_COD_COLLECTION("货到付款收款"),

    /**
     * 分笔 COD 来源：{@code source_id = xsy_idempotency_record.id}，订单由 cod_order_id 指明。
     */
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
