package com.xsy.scm.payment.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 回调事件类型。持久化到 {@code payment_callback_event.event_type}。
 *
 * <p>
 * 这是<b>归一化后</b>的类型：各渠道的原始事件名在 provider 里翻译成这四个之一， 业务域只认归一化结果，因此新增渠道不需要在状态机里加分支。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPaymentCallbackEventTypeEnum {

    PAYMENT_SUCCEEDED("支付成功"),

    PAYMENT_FAILED("支付失败"),

    REFUND_SUCCEEDED("退款成功"),

    REFUND_FAILED("退款失败");

    private final String desc;

    public static boolean isSupported(String value) {
        for (ScmPaymentCallbackEventTypeEnum item : values()) {
            if (item.name().equals(value)) {
                return true;
            }
        }
        return false;
    }

    public static ScmPaymentCallbackEventTypeEnum of(String value) {
        for (ScmPaymentCallbackEventTypeEnum item : values()) {
            if (item.name().equals(value)) {
                return item;
            }
        }
        return null;
    }
}
