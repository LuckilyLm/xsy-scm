package com.xsy.scm.mall.order.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 商城只展示能够完成支付的余额、账期和货到付款选项。在线支付未接入完整渠道前不开放，结算方式本身不直接执行资金操作。
 */
@Getter
@RequiredArgsConstructor
public enum ScmSettlementMethodEnum {

    /**
     * 余额支付：下单前用钱包余额直接支付。
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
