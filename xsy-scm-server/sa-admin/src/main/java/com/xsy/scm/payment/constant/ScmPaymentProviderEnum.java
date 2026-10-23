package com.xsy.scm.payment.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 支付渠道。保存到 {@code xsy_payment_intent.provider} 等列。
 *
 * <p>
 * 渠道是「同一份业务规则跑在不同实现上」的开关：换渠道只换 {@code ScmPaymentProvider} 实现， 业务域里不出现 {@code if ("wechat")}。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPaymentProviderEnum {

    /** 本地模拟渠道：覆盖成功 / 失败 / 延迟 / 过期，以及退款与对账。 */
    MOCK("本地模拟"),

    /** 微信支付：尚未接入，此处只占位以固定接口；密钥与通知地址不得进业务规则。 */
    WECHAT("微信支付"),

    INTERNAL_BALANCE("内部余额结算");

    private final String desc;

    public static boolean isSupported(String value) {
        for (ScmPaymentProviderEnum item : values()) {
            if (item.name().equals(value)) {
                return true;
            }
        }
        return false;
    }

    public static ScmPaymentProviderEnum of(String value) {
        for (ScmPaymentProviderEnum item : values()) {
            if (item.name().equals(value)) {
                return item;
            }
        }
        return null;
    }
}
