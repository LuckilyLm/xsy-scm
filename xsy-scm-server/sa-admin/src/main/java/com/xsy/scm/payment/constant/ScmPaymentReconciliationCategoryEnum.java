package com.xsy.scm.payment.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 对账分类供人工查看和处理；对账程序只发现差异，不自动修改支付记录。匹配成功不是差异记录。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPaymentReconciliationCategoryEnum {

    /** 渠道收到了钱，本地没有对应交易 —— 最危险的一类（客户付了、账上没记）。 */
    LOCAL_MISSING("本地缺失"),

    /** 本地记了成功，渠道账上没有 —— 可能本地误记，也可能是渠道账还没出。 */
    PROVIDER_MISSING("渠道缺失"),

    /** 两边都有，金额不一致。这正是 {@code amount} 与 {@code provider_amount} 分开存的原因。 */
    AMOUNT_MISMATCH("金额不符"),

    /** 本地不是成功态，渠道账上却有这笔 —— 状态与实际不符。 */
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
