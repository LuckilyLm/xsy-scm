package com.xsy.scm.mall.order.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 商城结算方式。
 *
 * <p>
 * 只收录当前能真正闭环的三种：余额、账期、货到付款。在线支付需要真实渠道回调与对账， 未接入前不对外开放，因此不在此枚举内 —— 避免出现「选了却收不到钱」的伪能力。
 *
 * <p>
 * 枚举只表达客户在结算页的「约定」，不承担资金动作：余额扣减走钱包、账期占用走授信、 货到付款只是交付时收款，各自的时点与幂等由对应领域负责。
 */
@Getter
@RequiredArgsConstructor
public enum ScmSettlementMethodEnum {

    /** 余额支付：下单前用钱包余额直接支付。 */
    WALLET("余额支付"),

    /** 账期结算：按客户授信与账期规则，成交后计入应收。 */
    CREDIT("账期结算"),

    /** 货到付款：下单只记录结算约定，交付时按实际交付金额收款。 */
    COD("货到付款");

    private final String desc;

    public static boolean isSupported(String value) {
        if (value == null) {
            return false;
        }
        for (ScmSettlementMethodEnum item : values()) {
            if (item.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
