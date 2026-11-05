package com.xsy.scm.mall.order.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 正式订单的结算约定。新商城订单由后台确认时选择，结算方式本身不直接执行资金操作。
 */
@Getter
@RequiredArgsConstructor
public enum ScmSettlementMethodEnum {

    /**
     * 余额结算：资金由后台正式支付命令扣减，不在商城下单时扣减。
     */
    WALLET("余额支付"),

    /**
     * 账期结算：按客户授信与账期规则，成交后计入应收。
     */
    CREDIT("账期结算"),

    /**
     * 货到付款：下单只记录结算约定，交付时按实际交付金额收款。
     */
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
