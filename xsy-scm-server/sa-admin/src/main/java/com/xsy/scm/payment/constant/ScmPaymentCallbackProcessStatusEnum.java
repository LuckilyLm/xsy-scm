package com.xsy.scm.payment.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 回调事件的处理结果。持久化到 {@code payment_callback_event.process_status}。
 *
 * <p>
 * 四个值刻意区分「收下了但没处理」与「处理失败」：未通过验签的事件<b>照样落库</b>（留证）， 但永远是 {@code REJECTED}，绝不进入状态转换 —— 这样「有人伪造回调」是看得见的， 而不是被静默丢弃。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPaymentCallbackProcessStatusEnum {

    /** 已收下，待处理（同步处理路径下基本不会停留在此）。 */
    RECEIVED("已接收"),

    /** 验签通过且状态转换已应用。 */
    APPLIED("已应用"),

    /** 同一渠道事件 id 已存在：<b>没有重复执行任何副作用</b>。 */
    DUPLICATE("重复事件"),

    /** 验签失败或报文不合法：只留证，不处理。 */
    REJECTED("已拒绝");

    private final String desc;

    public static ScmPaymentCallbackProcessStatusEnum of(String value) {
        for (ScmPaymentCallbackProcessStatusEnum item : values()) {
            if (item.name().equals(value)) {
                return item;
            }
        }
        return null;
    }
}
