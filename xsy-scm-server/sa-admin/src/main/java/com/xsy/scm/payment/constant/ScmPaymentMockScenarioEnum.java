package com.xsy.scm.payment.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 仅模拟渠道可以携带测试场景。模拟场景只控制渠道行为，不得绕过验签、幂等或状态机。
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
     * 这是最值得先建好的场景：真实渠道的回调几乎总是异步且可能晚到，「发起后立刻返回成功」的假 mock 会掩盖整类时序缺陷。
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
