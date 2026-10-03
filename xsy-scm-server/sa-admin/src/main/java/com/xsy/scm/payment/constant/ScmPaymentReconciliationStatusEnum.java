package com.xsy.scm.payment.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 对账状态。持久化到 {@code payment_reconciliation.status}。 */
@Getter
@RequiredArgsConstructor
public enum ScmPaymentReconciliationStatusEnum {

    /** 已拉取渠道账，尚未比对。 */
    PENDING("待比对"),

    /** 差额为 0（DDL 有 CHECK 保证）。 */
    MATCHED("已平账"),

    /** 有差额，差异明细落在 {@code detail}。 */
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
