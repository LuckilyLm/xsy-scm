package com.xsy.scm.payment.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.error.ScmErrorCode;

/**
 * 支付域使用 41340–41359 错误码（41320–41339 属营销域）。
 *
 * <p>
 * 分配前已 grep 全仓已用码值确认 41340–41359 空闲（既有最大值为 41333）。
 */
@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ScmErrorCode {

    PAYMENT_INTENT_NOT_FOUND(41340, "支付意图不存在或已被删除"),

    /** 状态机不允许的转换：例如已成功的意图再关闭、已关闭的意图再支付。 */
    PAYMENT_INTENT_STATE_INVALID(41341, "支付意图当前状态不允许此操作"),

    PAYMENT_PROVIDER_UNSUPPORTED(41342, "不支持的支付渠道"),

    /** 回调验签失败：<b>不进入状态转换</b>，只落事件留证。 */
    PAYMENT_CALLBACK_SIGNATURE_INVALID(41343, "支付回调验签失败"),

    /** 回调报文格式不合法（缺事件 id / 金额 / 交易号等必需字段）。 */
    PAYMENT_CALLBACK_PAYLOAD_INVALID(41344, "支付回调报文不合法"),

    PAYMENT_TRANSACTION_NOT_FOUND(41345, "支付交易不存在"),

    PAYMENT_REFUND_NOT_FOUND(41346, "支付退款不存在"),

    PAYMENT_REFUND_STATE_INVALID(41347, "支付退款当前状态不允许此操作"),

    /** 退款额超过该笔交易已成功收到的金额。 */
    PAYMENT_REFUND_AMOUNT_EXCEEDED(41348, "退款金额超过该笔交易的可退金额"),

    PAYMENT_RECONCILIATION_NOT_FOUND(41349, "对账记录不存在"),

    /** 同一渠道同一业务日已出过对账结论：不允许重复生成，避免两份互相矛盾的结论。 */
    PAYMENT_RECONCILIATION_DUPLICATED(41350, "该渠道该业务日已完成对账"),

    /** 仅 MOCK 渠道可指定剧本；真实渠道带剧本一律拒收。 */
    PAYMENT_MOCK_SCENARIO_INVALID(41351, "只有本地模拟渠道可以指定回放剧本"),

    /** 渠道回报金额与本地应付金额不一致：<b>不自动改账</b>，落差异交由对账处理。 */
    PAYMENT_PROVIDER_AMOUNT_MISMATCH(41352, "渠道回报金额与本地应付金额不一致"),

    /**
     * 该支付方式尚未启用。
     *
     * <p>
     * 余额（{@code BALANCE}）的余额流水与扣减是 ADM-12 3-12 的内容；在它落地之前， 这里<b>明确拒绝</b>而不是让它悄悄走到外部渠道上去。
     */
    PAYMENT_METHOD_NOT_ENABLED(41353, "该支付方式尚未启用"),

    /** 同一业务退款来源只能映射一笔有效退款：重复来源说明上游重复提交，宁可失败也不重复出款。 */
    PAYMENT_REFUND_SOURCE_DUPLICATED(41354, "该业务退款来源已存在有效退款"),

    /**
     * 成功的交易缺少可信的渠道实收金额。
     *
     * <p>
     * 可退本金必须以<b>渠道实际成功捕获/结算的金额</b>为准。缺失时<b>拒绝退款</b>而不是退回本地应付金额： 静默 fallback 会掩盖「支付结果没落完整」这个问题，等接真实渠道时才以「退款被渠道拒」的形式暴露。
     */
    PAYMENT_PROVIDER_AMOUNT_MISSING(41355, "该笔交易缺少渠道实收金额，无法确定可退本金"),

    /** 业务退款来源不成立：退款单不存在 / 未完成 / 客户与原支付不一致 / 来源类型不受支持。 */
    PAYMENT_REFUND_SOURCE_INVALID(41356, "业务退款来源不合法：请确认退款单已完成且属于原支付客户"),

    /** 提交的退款额与业务退款单的应退额<b>逐值不一致</b>。 */
    PAYMENT_REFUND_AMOUNT_MISMATCH(41357, "退款金额与业务退款单的应退金额不一致"),

    /** 渠道退款成功回调没带实际退款金额：没有它就无法登记资金反向事实。 */
    PAYMENT_REFUND_PROVIDER_AMOUNT_MISSING(41358, "渠道未回报实际退款金额，无法确认退款结果"),

    /**
     * 支付意图的业务来源不合法：订单不存在 / 不受支持 / 与提交的客户不一致。
     *
     * <p>
     * <b>来源身份不能由客户端自己拼</b>：意图必须按订单 id 解析出正式事实（订单号、客户、业务员）， 否则 {@code customer_name_snapshot} 里会躺着一个客户 id 字符串，支付成功后又顺着它进
     * Finance。
     */
    PAYMENT_INTENT_SOURCE_INVALID(41359, "支付意图的业务来源不合法：请确认订单存在且属于该客户");

    private final int code;

    private final String msg;
}
