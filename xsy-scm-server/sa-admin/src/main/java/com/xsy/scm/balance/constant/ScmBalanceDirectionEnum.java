package com.xsy.scm.balance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 余额流水方向。持久化到 {@code customer_balance_movement.direction}。 */
@Getter
@RequiredArgsConstructor
public enum ScmBalanceDirectionEnum {

    /** 增加余额（充值 / 退款返还 / 正数更正）。 */
    CREDIT("增加"),

    /** 减少余额（消费 / 负数更正）。 */
    DEBIT("减少");

    private final String desc;

    public static boolean isSupported(String value) {
        for (ScmBalanceDirectionEnum item : values()) {
            if (item.name().equals(value)) {
                return true;
            }
        }
        return false;
    }

    public static ScmBalanceDirectionEnum of(String value) {
        for (ScmBalanceDirectionEnum item : values()) {
            if (item.name().equals(value)) {
                return item;
            }
        }
        return null;
    }
}
