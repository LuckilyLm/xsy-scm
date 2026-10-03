package com.xsy.scm.payment.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 支付方式（渠道大类）。持久化到 {@code payment_intent.method}。 */
@Getter
@RequiredArgsConstructor
public enum ScmPaymentMethodEnum {

    /** 在线支付：走渠道（当前为本地模拟）。 */
    ONLINE("在线支付"),

    /** 客户余额抵扣：不经过外部渠道，但同样落 Finance 收款事实（3-12）。 */
    BALANCE("客户余额");

    private final String desc;

    public static boolean isSupported(String value) {
        for (ScmPaymentMethodEnum item : values()) {
            if (item.name().equals(value)) {
                return true;
            }
        }
        return false;
    }

    public static ScmPaymentMethodEnum of(String value) {
        for (ScmPaymentMethodEnum item : values()) {
            if (item.name().equals(value)) {
                return item;
            }
        }
        return null;
    }
}
