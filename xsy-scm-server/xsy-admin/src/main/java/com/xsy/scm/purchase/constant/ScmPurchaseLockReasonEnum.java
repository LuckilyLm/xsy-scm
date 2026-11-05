package com.xsy.scm.purchase.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 采购锁单原因码，写入 {@code xsy_purchase_order.lock_reason}。
 *
 * <p>
 * 只放「为什么锁」的分类；操作人手写的说明记在采购操作日志的 reason 上，两者不混用。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPurchaseLockReasonEnum {

    /** 财务或采购负责人手动锁单。 */
    MANUAL("手动锁单"),

    /** 提交满 7 天由定时任务自动锁单。 */
    AUTO_7_DAYS("提交满 7 天自动锁单");

    private final String desc;
}
