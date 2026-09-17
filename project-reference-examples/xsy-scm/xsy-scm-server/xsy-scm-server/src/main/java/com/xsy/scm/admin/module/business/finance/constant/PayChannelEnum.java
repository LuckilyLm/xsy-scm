package com.xsy.scm.admin.module.business.finance.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 收款渠道
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum PayChannelEnum implements BaseEnum {

    /**
     * 1 现金
     */
    CASH(1, "现金"),

    /**
     * 2 转账
     */
    TRANSFER(2, "转账"),

    /**
     * 3 在线支付
     */
    ONLINE(3, "在线支付"),

    /**
     * 4 余额扣减
     */
    BALANCE(4, "余额扣减"),

    ;

    private final Integer value;

    private final String desc;
}
