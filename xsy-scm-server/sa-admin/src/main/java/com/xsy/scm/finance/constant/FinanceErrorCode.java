package com.xsy.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.error.ScmErrorCode;

/**
 * 财务域错误码使用 41130–41149；已发布数值保持稳定。
 *
 * <p>
 * 需要隐藏资源是否存在的客户数据范围拒绝由 {@code ScmDataScopeException} 统一返回 30005； 其余错误码只表达财务命令本身的业务拒绝原因。
 */
@Getter
@RequiredArgsConstructor
public enum FinanceErrorCode implements ScmErrorCode {

    RECEIVABLE_NOT_FOUND(41130, "应收单不存在"),
    PAYABLE_NOT_FOUND(41131, "应付单不存在"),
    RECEIPT_NOT_FOUND(41132, "收款单不存在"),
    PAYMENT_NOT_FOUND(41133, "付款单不存在"),
    WRITE_OFF_NOT_FOUND(41134, "核销记录不存在"),

    /**
     * 核销额超过目标 {@code openAmount}，或超过 source 的待核销余额。
     *
     * <p>
     * 净应收为负时 {@code openAmount = 0}，该应收不可再被核销，仍使用本码。
     */
    WRITE_OFF_AMOUNT_EXCEEDED(41135, "核销金额超过可核销余额"),

    /**
     * 跨客户 / 跨供应商核销（明令禁止）。
     */
    COUNTERPARTY_MISMATCH(41136, "收付款与应收应付的结算对方不一致，不能核销"),

    /**
     * <b>仅</b>用于手工红字应付超额冲减。
     *
     * <p>
     * 自动红字应收**永不**使用本码：红字生成在 {@code OrderReturn approve} 的同一事务内， 抛错会让整笔退货批准回滚，等于财务规则反向控制订单域状态机。 手工红字应付可以
     * fail-loud，因为拒绝它不会回滚任何业务域状态机。
     */
    RED_AMOUNT_EXCEEDED(41137, "红字金额超过原单可冲减金额"),

    /**
     * 反向核销 / 手工红字 / 收付款反向缺原因。DB CHECK 已经拒绝空原因， 本码只为给出可解释的业务错误，而不是把 23514 抛给用户。
     */
    REVERSE_REASON_REQUIRED(41138, "该操作必须填写原因"),

    /**
     * 退款付款来源不合法：退款未 {@code COMPLETED}、金额不等于 {@code refund_amount}、 或对方与该退款的客户不一致。
     */
    PAYMENT_SOURCE_INVALID(41139, "退款付款来源不合法"),

    METHOD_INVALID(41140, "收付款方式不在允许范围内"),

    RECEIVED_AT_INVALID(41141, "收付款时点不合法"),

    /**
     * 收款或付款仍有有效核销额时不得反向，必须先逐笔反向核销并把已用额降至 0。
     *
     * <p>
     * 不设这条前置就会出现「已用 &gt; 有效额」的负待核销余额，与红字造成的负净应收叠加后 无法向用户解释 —— 负值只允许出现在「应收侧忠实记录已成立退货」这一处。
     */
    REVERSE_BLOCKED_BY_WRITE_OFF(41142, "该单仍有有效核销，请先撤销相关核销"),

    /**
     * 该事实已被反向过。{@code uk_finance_*_single_reverse} 是最终防线， 本码让服务层在撞库级唯一索引之前给出可读错误。
     */
    ALREADY_REVERSED(41143, "该单据已被反向，不能重复反向");

    private final int code;
    private final String msg;
}
