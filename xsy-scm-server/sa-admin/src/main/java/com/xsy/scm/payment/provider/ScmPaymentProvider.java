package com.xsy.scm.payment.provider;

import com.xsy.scm.payment.constant.ScmPaymentCallbackEventTypeEnum;
import com.xsy.scm.payment.constant.ScmPaymentMockScenarioEnum;
import com.xsy.scm.payment.constant.ScmPaymentProviderEnum;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 支付渠道契约（provider-neutral）。
 *
 * <p>
 * <b>业务域只认这个接口，不认任何渠道</b>：状态机、幂等、Finance 收款映射都在业务侧，
 * 换渠道只换实现。渠道密钥、签名算法、通知地址全部封装在实现里，
 * **不得出现在业务规则、配置以外的类或客户端**（ADR-009 第 17 条）。
 *
 * <p>
 * <b>实现必须是纯函数式的「问渠道 / 让渠道做事」</b>：不写库、不开事务、不改业务状态。
 * 状态转换由 {@code PaymentCallbackService} 在事务里做 —— 否则「渠道调用成功但事务回滚」
 * 会留下说不清的外部事实。
 *
 * <p>
 * 嵌套 record 而不是一堆顶层文件：契约集中在一处，改接口时不可能漏改某个 DTO。
 */
public interface ScmPaymentProvider {

    /** 本实现对应的渠道。 */
    ScmPaymentProviderEnum provider();

    /**
     * 向渠道发起支付。
     *
     * @param request
     *            只含渠道需要的信息（金额、摘要、本地单号）；不含客户隐私与订单明细
     */
    IntentResult createIntent(IntentRequest request);

    /**
     * 向渠道发起退款。
     */
    RefundResult refund(RefundRequest request);

    /**
     * 验签并归一化回调。
     *
     * <p>
     * <b>不抛异常</b>：验签失败或报文不合法时返回 {@code signatureVerified = false} 并带上
     * {@code rejectReason}，由业务侧落一条 {@code REJECTED} 事件留证 ——
     * 「有人伪造回调」必须是看得见的，而不是被异常吞掉。
     */
    Callback parseCallback(Map<String, String> headers, String rawBody);

    /**
     * 查单（对账与补偿用）。查不到返回 {@code null}。
     */
    TransactionResult queryTransaction(String providerTransactionNo);

    /**
     * 拉取某业务日的渠道**收款**明细。
     *
     * <p>
     * 首版只对收款：退款的渠道账与本地退款事实是另一组口径，混进同一个合计会让
     * 「渠道合计含退款、本地合计不含」这种不对称的比较永远报差异 —— 那不是发现了问题，
     * 是拿两把尺子量。退款对账作为后续独立一项。
     */
    Settlement fetchSettlement(LocalDate bizDate);

    /** 渠道发起结果。 */
    enum Outcome {
        PENDING, SUCCEEDED, FAILED
    }

    /**
     * 发起支付入参。
     *
     * @param scenario
     *            仅本地模拟渠道使用；真实渠道实现必须忽略它
     */
    record IntentRequest(String intentNo, BigDecimal amount, String subject, ScmPaymentMockScenarioEnum scenario) {
    }

    /**
     * 发起支付结果。
     *
     * @param providerTransactionNo
     *            渠道交易号；回调按它匹配本地交易
     * @param amount
     *            **渠道确认收到的金额**。只有同步成功（{@code SUCCEEDED}）才有值；
     *            延迟 / 失败时为空，等回调再报。它是 Finance 收款金额的唯一依据 ——
     *            本地绝不拿应付金额去顶替它
     */
    record IntentResult(String externalIntentId, String providerTransactionNo, Outcome outcome, BigDecimal amount,
            String failureCode, String failureMessage) {
    }

    /** 退款入参。 */
    record RefundRequest(String refundNo, String providerTransactionNo, BigDecimal amount, String reason,
            ScmPaymentMockScenarioEnum scenario) {
    }

    /**
     * 退款结果。
     *
     * @param amount
     *            **渠道实际退回的金额**；只有成功时才有值。与申请退款额分开：
     *            申请 100 而渠道实退 98 是可能发生的，资金反向事实认的是它
     */
    record RefundResult(String providerRefundNo, Outcome outcome, BigDecimal amount, String failureCode,
            String failureMessage) {
    }

    /**
     * 归一化后的回调。
     *
     * @param providerEventId
     *            渠道事件 id —— **回调幂等的主锚点**
     * @param eventType
     *            归一化事件类型；类型不认识时为 {@code null}
     * @param rawEventType
     *            渠道原始事件名。类型不认识时**原样保留**：落库留证比编一个本地占位值诚实
     *            （也避免在业务代码里造一个与其它域撞值的魔法字符串）
     * @param providerTransactionNo
     *            渠道交易号
     * @param amount
     *            渠道回报金额（与本地应付分开记）
     * @param providerRefundNo
     *            退款回调才有
     * @param payload
     *            原文解析结果，落库留证
     * @param signatureVerified
     *            验签结果
     * @param rejectReason
     *            验签或解析失败的原因
     */
    record Callback(String providerEventId, ScmPaymentCallbackEventTypeEnum eventType, String rawEventType,
            String providerTransactionNo, BigDecimal amount, String providerRefundNo, Map<String, Object> payload,
            boolean signatureVerified, String rejectReason) {
    }

    /** 查单结果。 */
    record TransactionResult(String providerTransactionNo, BigDecimal amount, Outcome outcome, OffsetDateTime paidAt) {
    }

    /**
     * 渠道对账明细。
     *
     * @param total
     *            渠道侧合计
     * @param lines
     *            逐笔明细，用于定位差异
     */
    record Settlement(LocalDate bizDate, BigDecimal total, int count, List<SettlementLine> lines) {
    }

    /** 渠道对账的一行。 */
    record SettlementLine(String providerTransactionNo, BigDecimal amount) {
    }
}
