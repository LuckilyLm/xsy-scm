package com.xsy.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 过账单据在接口层的有效状态。
 *
 * <p>
 * 不落库：是否作废由 {@code xsy_finance_posting_void} 有没有对应行推导（{@code posting_id} 唯一）。 过账事实本身 append-only，作废是追加事实，不是改状态。
 */
@Getter
@RequiredArgsConstructor
public enum ScmFinancePostingStatusEnum {

    /**
     * 有效。
     */
    NORMAL("有效"),

    /**
     * 已作废，原事实保留但不再代表对外账目。
     */
    VOIDED("已作废");

    private final String desc;
}
