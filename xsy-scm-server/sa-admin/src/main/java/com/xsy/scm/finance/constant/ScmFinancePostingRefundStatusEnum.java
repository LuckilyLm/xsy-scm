package com.xsy.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 过账单据的返款状态，与 {@code xsy_finance_posting.refund_status} 的 CHECK 白名单一致。
 *
 * <p>
 * 只记录状态，不记录返款金额：现场口径说的是「这笔过账的钱返没返」， 没有要求系统按返款金额再算一次余额，多一个金额列就多一份无人核对的账。
 */
@Getter
@RequiredArgsConstructor
public enum ScmFinancePostingRefundStatusEnum {

    /**
     * 未返款。
     */
    PENDING("未返款"),

    /**
     * 部分返款。
     */
    PARTIAL("部分返款"),

    /**
     * 已返款。
     */
    REFUNDED("已返款");

    private final String desc;
}
