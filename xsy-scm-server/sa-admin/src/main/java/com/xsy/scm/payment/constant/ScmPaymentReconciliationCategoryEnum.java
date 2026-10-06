package com.xsy.scm.payment.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 对账差异分类。持久化到 {@code payment_reconciliation_item.category}。
 *
 * <p>
 * 四类都是<b>要给人看的结论</b>，不是内部状态：后台按分类展示，人工处理。 对账程序<b>只发现差异、不自动修复</b> —— 自动改交易状态会让审计链变得说不清。
 *
 * <p>
 * {@code MATCHED} 不在这里：它是「零差异」这个运行结论（{@code payment_reconciliation.status}）， 不是一条差异记录。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPaymentReconciliationCategoryEnum {

    /** 渠道收到了钱，本地没有对应交易 —— 最危险的一类（客户付了、账上没记）。 */
    LOCAL_MISSING("本地缺失"),

    /** 本地记了成功，渠道账上没有 —— 可能本地误记，也可能是渠道账还没出。 */
    PROVIDER_MISSING("渠道缺失"),

    /** 两边都有，金额不一致。这正是 {@code amount} 与 {@code provider_amount} 分开存的价值所在。 */
    AMOUNT_MISMATCH("金额不符"),

    /** 本地不是成功态，渠道账上却有这笔 —— 状态与事实不符。 */
    STATUS_MISMATCH("状态不符");

    private final String desc;

    public static boolean isSupported(String value) {
        for (ScmPaymentReconciliationCategoryEnum item : values()) {
            if (item.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
