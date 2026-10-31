package com.xsy.scm.finance.constant;

import com.xsy.scm.common.error.ScmErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 财务域初始码段为 41130–41149；已发布的数值保持不变，新增值按全域注册清单选空号。
 *
 * <p>
 * 数据范围拒绝统一由 {@code ScmDataScopeException} 返回 30005，其余错误码只表达财务命令本身的拒绝原因。
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
     * 只用于手工红字应付超额冲减。
     *
     * <p>
     * 自动红字应收不使用本码：红字在 {@code OrderReturn approve} 的同一事务内生成，抛错会让整笔退货批准回滚，等于财务规则反过来控制订单状态。 手工红字应付可以直接报错，因为拒绝它不会回滚任何业务状态。
     */
    RED_AMOUNT_EXCEEDED(41137, "红字金额超过原单可冲减金额"),

    /**
     * 反向核销 / 手工红字 / 收付款反向缺原因。数据库 CHECK 已拒绝空原因，本码只为给出可读的业务错误，而不是把 23514 抛给用户。
     */
    REVERSE_REASON_REQUIRED(41138, "该操作必须填写原因"),

    /**
     * 退款付款来源不合法：退款未 {@code COMPLETED}、金额不等于 {@code refund_amount}、或对方与该退款的客户不一致。
     */
    PAYMENT_SOURCE_INVALID(41139, "退款付款来源不合法"),

    METHOD_INVALID(41140, "收付款方式不在允许范围内"),

    RECEIVED_AT_INVALID(41141, "收付款时点不合法"),

    /**
     * 收款或付款仍有有效核销额时不得反向，必须先逐笔反向核销并把已用额降至 0。
     *
     * <p>
     * 没有这条前置就会出现「已用 &gt; 有效额」的负待核销余额，与红字造成的负净应收叠加后无法向用户解释；负值只允许出现在「应收侧记录已成立退货」这一处。
     */
    REVERSE_BLOCKED_BY_WRITE_OFF(41142, "该单仍有有效核销，请先撤销相关核销"),

    /**
     * 该事实已被反向过。{@code uk_finance_*_single_reverse} 是最终防线，本码让服务层在撞库级唯一索引之前给出可读错误。
     */
    ALREADY_REVERSED(41143, "该单据已被反向，不能重复反向"),

    /**
     * 系统来源的收款不允许走人工冲正。
     *
     * <p>
     * 人工 {@code reverse()} 的语义是「整笔登记错了，撤销这笔登记」，且要求原收款已核销金额为 0；支付退款则是业务退款，可能部分、 可能多次（100 退 30 再退 20），现有 REVERSE
     * 模型表达不了，应走独立的系统资金反向记录。
     */
    SYSTEM_RECEIPT_REVERSE_FORBIDDEN(41144, "系统来源的收款不能人工冲正，请走支付退款流程"),

    /**
     * 支付交易缺少可信的渠道实收金额：不能凭空登记一笔金额不明的收款。
     */
    PAYMENT_RECEIPT_AMOUNT_MISSING(41145, "支付交易缺少渠道实收金额，无法登记收款"),
    ORDER_FUNDING_INVALID(41146, "订单资金来源不完整或身份金额不一致，请核对支付与资金流水"),
    RECHARGE_RECEIPT_RESERVED(41147, "充值收款已形成钱包权益，请通过余额支付结算订单"),
    REFUND_ALLOCATION_REQUIRED(41148, "该退款需要支付来源分摊，不能从单笔交易或人工付款直接退款"),
    BALANCE_REFUND_REQUIRES_WALLET(41149, "纯余额订单请通过售后退款单返还钱包余额"),
    BALANCE_PAYMENT_AFTER_REFUND(41150, "订单已发起资金退款，暂不支持继续余额支付"),
    PAYMENT_AFTER_BALANCE_REFUND(41151, "订单已返还余额，不能继续发起支付，请另建订单"),

    /**
     * 货到付款收款：订单不存在。
     */
    COD_COLLECTION_ORDER_NOT_FOUND(41152, "订单不存在"),
    /**
     * 货到付款收款：订单不是 COD 结算，或尚未签收生成应收（拒收 / 异常签收就没有应收可收）。
     */
    COD_COLLECTION_ORDER_NOT_APPLICABLE(41153, "该订单不是货到付款结算，或尚未签收生成应收，不能登记收款"),
    /**
     * 同一货到付款收款命令已有来源事实。
     */
    COD_COLLECTION_ALREADY_REGISTERED(41154, "该笔货到付款收款已登记，不能重复登记"),
    COD_COLLECTION_PAYMENT_CONFLICT(41155, "该订单已有未结束或成功的支付意图，不能再登记货到付款收款"),

    // ---- 抹零 ----
    WAIVER_NOT_FOUND(41156, "抹零记录不存在"),
    WAIVER_AMOUNT_EXCEEDED(41157, "抹零金额超过应收未结金额"),
    WAIVER_RECEIVABLE_INVALID(41158, "只能对正常应收抹零，且金额必须大于 0"),
    WAIVER_ALREADY_REVERSED(41159, "该抹零记录已反向，不能重复反向"),

    // ---- 自动核销 ----
    AUTO_WRITE_OFF_NO_OPEN_TARGET(41164, "该收付款对方没有可按顺序核销的未结单据"),

    /**
     * 抹零的核销行不能走通用「反向核销」：它还必须把抹零事实置为 REVERSED，否则状态与余额会不一致。
     */
    WAIVER_REVERSE_VIA_WAIVER(41165, "抹零核销请通过抹零反向接口撤销"),

    // ---- 过账特殊单据 ----
    POSTING_NOT_FOUND(41184, "过账记录不存在"),
    POSTING_ALREADY_VOIDED(41185, "该过账记录已作废，不能重复作废或修改"),
    POSTING_CUSTOMER_NOT_FOUND(41186, "过账客户不存在或已删除"),
    POSTING_VOID_REASON_REQUIRED(41187, "作废过账必须填写作废原因"),
    POSTING_AMOUNT_INVALID(41189, "过账金额必须大于 0"),
    POSTING_TAX_RATE_INVALID(41190, "税点必须在 0 到 100 之间"),
    POSTING_REFUND_STATUS_INVALID(41191, "返款状态只能是未返款、部分返款或已返款"),

    EXPENSE_NOT_FOUND(41290, "客户或费用记录不存在"),
    EXPENSE_AMOUNT_INVALID(41291, "成本费用不能为负；请登记实际商品成本或至少一项费用"),
    EXPENSE_MONTH_INVALID(41292, "请选择有效的费用月份"),
    EXPENSE_ALREADY_VOIDED(41293, "该费用记录已作废");

    private final int code;
    private final String msg;
}
