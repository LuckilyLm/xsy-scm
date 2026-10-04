package com.xsy.scm.balance.constant;

import com.xsy.scm.common.error.ScmErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 余额域使用 41360–41379 错误码。
 *
 * <p>
 * 分配前已 grep 全仓已用码值：既有区间到 41359（支付域 41340–41359 已用满），41360 起空闲。
 */
@Getter
@RequiredArgsConstructor
public enum BalanceErrorCode implements ScmErrorCode {

    BALANCE_ACCOUNT_NOT_FOUND(41360, "客户余额账户不存在"),

    /** 余额不足：**不允许负余额**，宁可失败也不透支。 */
    BALANCE_INSUFFICIENT(41361, "客户余额不足"),

    BALANCE_MOVEMENT_NOT_FOUND(41362, "余额流水不存在"),

    /** 金额必须为正：方向由 direction 表达，金额为 0 或负数没有意义。 */
    BALANCE_AMOUNT_INVALID(41363, "余额变动金额必须大于0"),

    /** 方向不合法：该流水类型固定了方向（例如充值只能增加），调用方不得改写。 */
    BALANCE_DIRECTION_INVALID(41364, "余额变动方向不合法"),

    /** 流水类型不合法。 */
    BALANCE_TYPE_INVALID(41365, "余额流水类型不合法"),

    /** 人工更正必须写原因。 */
    BALANCE_REASON_REQUIRED(41366, "人工更正余额必须填写原因"),

    /** 来源类型与来源 id 必须成对出现。 */
    BALANCE_SOURCE_INVALID(41367, "余额流水来源不合法"),

    /** 同一来源已经产生过余额流水：重复驱动不重复入账。 */
    BALANCE_SOURCE_DUPLICATED(41368, "该来源已产生过余额流水"),

    /** 充值请求不存在。 */
    BALANCE_RECHARGE_NOT_FOUND(41369, "充值请求不存在"),

    /** 充值金额不合法。 */
    BALANCE_RECHARGE_AMOUNT_INVALID(41370, "充值金额必须大于0"),
    BALANCE_REFUND_SOURCE_INVALID(41371, "余额返还来源无效，请确认退款已完成且原订单为纯余额支付"),
    BALANCE_REFUND_AMOUNT_EXCEEDED(41372, "累计余额返还不能超过原订单已消费本金"),
    BALANCE_REFUND_PATH_CONFLICT(41373, "该订单已有渠道或人工资金退款，不能再返还余额"),
    BALANCE_REFUND_PENDING_PAYMENT(41374, "订单仍有未完成支付，请先处理支付结果再返还余额");

    private final int code;

    private final String msg;
}
