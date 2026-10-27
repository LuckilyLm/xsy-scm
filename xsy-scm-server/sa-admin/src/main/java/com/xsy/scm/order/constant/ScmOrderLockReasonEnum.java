package com.xsy.scm.order.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 锁单原因码，写入 {@code xsy_sales_order.lock_reason}。
 *
 * <p>
 * 这里只放「为什么锁」的分类；操作人手写的说明记在订单操作日志的 reason 上，两者不混用。
 */
@Getter
@RequiredArgsConstructor
public enum ScmOrderLockReasonEnum {

    /** 财务或管理员手动锁单。 */
    MANUAL("手动锁单"),

    /** 发车出库满 7 天由定时任务自动锁单。 */
    AUTO_7_DAYS("发车满 7 天自动锁单");

    private final String desc;
}
