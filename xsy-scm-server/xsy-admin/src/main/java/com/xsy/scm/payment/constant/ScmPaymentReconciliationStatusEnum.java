package com.xsy.scm.payment.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 对账状态。保存到 {@code xsy_payment_reconciliation.status}。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPaymentReconciliationStatusEnum {

    /** 已拉取渠道账，尚未比对。 */
    PENDING("待比对"),

    /** 差额为 0（数据库 CHECK 约束保证）。 */
    MATCHED("已平账"),

    /** 有差额，差异明细写在 {@code detail}。 */
    MISMATCHED("有差异");

    private final String desc;

    public static ScmPaymentReconciliationStatusEnum of(String value) {
        for (ScmPaymentReconciliationStatusEnum item : values()) {
            if (item.name().equals(value)) {
                return item;
            }
        }
        return null;
    }
}
