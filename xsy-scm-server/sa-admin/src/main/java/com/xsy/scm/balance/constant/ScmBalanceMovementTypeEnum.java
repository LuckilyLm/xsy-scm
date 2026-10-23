package com.xsy.scm.balance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 每种钱包流水只允许规定的收支方向：充值入账、消费扣款、退款返还入账；人工更正可双向。
 */
@Getter
@RequiredArgsConstructor
public enum ScmBalanceMovementTypeEnum {

    /**
     * 充值：真实资金已进入（在线支付成功）后，把等额权益记进钱包。只能增加。
     */
    RECHARGE("充值", ScmBalanceDirectionEnum.CREDIT),

    /**
     * 消费：用余额抵扣，只能减少。
     */
    CONSUME("消费", ScmBalanceDirectionEnum.DEBIT),

    /**
     * 退款返还：原消费退款回到钱包，只能增加。
     */
    REFUND("退款返还", ScmBalanceDirectionEnum.CREDIT),

    /**
     * 人工更正：管理员对账后修正。<b>两个方向都可以</b>，因此必须显式给方向与原因。
     */
    CORRECTION("人工更正", null);

    private final String desc;

    /**
     * 该类型固定的方向；{@code null} 表示方向由调用方显式指定（仅 CORRECTION）。
     */
    private final ScmBalanceDirectionEnum fixedDirection;

    /**
     * 该类型是否允许指定方向。
     */
    public boolean directionSelectable() {
        return fixedDirection == null;
    }

    public static boolean isSupported(String value) {
        for (ScmBalanceMovementTypeEnum item : values()) {
            if (item.name().equals(value)) {
                return true;
            }
        }
        return false;
    }

    public static ScmBalanceMovementTypeEnum of(String value) {
        for (ScmBalanceMovementTypeEnum item : values()) {
            if (item.name().equals(value)) {
                return item;
            }
        }
        return null;
    }
}
