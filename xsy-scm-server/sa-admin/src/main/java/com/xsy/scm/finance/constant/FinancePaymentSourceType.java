package com.xsy.scm.finance.constant;

/**
 * 财务事实的系统来源类型（{@code finance_receipt.source_type} / 将来 {@code finance_payment.source_type}）。
 *
 * <p>
 * 取值由 Java 常量与 {@code ck_finance_receipt_source_type} 双向固定，**不允许自由填写**：
 * 来源类型一旦可以随意填，来源唯一索引就形同虚设 —— 换个名字就能为同一笔钱再登记一次收款。
 */
public final class FinancePaymentSourceType {

    /** 来源是支付域的交易事实：{@code source_id = payment_transaction.id}。 */
    public static final String PAYMENT_TRANSACTION = "PAYMENT_TRANSACTION";

    private FinancePaymentSourceType() {
    }
}
