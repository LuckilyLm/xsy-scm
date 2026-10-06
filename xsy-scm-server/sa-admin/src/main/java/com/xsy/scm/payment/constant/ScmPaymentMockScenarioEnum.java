package com.xsy.scm.payment.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 本地模拟渠道的回放剧本。
 *
 * <p>
 * <b>只有 MOCK 渠道能带剧本</b>（DDL 的 {@code ck_payment_intent_mock_scenario} 保证）： 真实渠道如果允许客户端指定剧本，就等于给了一个可控的行为开关。
 *
 * <p>
 * 剧本只影响 <b>provider 侧的行为</b>（什么时候回、回成功还是失败）， 不绕过验签、幂等与状态机 —— 那些是业务域的事，mock 无权跳过。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPaymentMockScenarioEnum {

    /** 立即成功。 */
    SUCCESS("立即成功"),

    /** 立即失败（渠道拒付）。 */
    FAILURE("立即失败"),

    /**
     * 延迟成功：发起时不返回结果，需要外部再投一次回调。
     *
     * <p>
     * 这是最值得先建好的场景：真实渠道的回调几乎总是异步且可能晚到， 「发起后立刻返回成功」的假 mock 会掩盖整类时序缺陷。
     */
    DELAYED("延迟回调"),

    /** 未支付即过期：发起后不产生成功回调。 */
    EXPIRED("超时未付");

    private final String desc;

    public static ScmPaymentMockScenarioEnum of(String value) {
        for (ScmPaymentMockScenarioEnum item : values()) {
            if (item.name().equals(value)) {
                return item;
            }
        }
        return null;
    }
}
