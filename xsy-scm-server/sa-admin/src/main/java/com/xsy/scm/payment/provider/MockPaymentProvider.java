package com.xsy.scm.payment.provider;

import com.xsy.scm.payment.constant.ScmPaymentCallbackEventTypeEnum;
import com.xsy.scm.payment.constant.ScmPaymentMockScenarioEnum;
import com.xsy.scm.payment.constant.ScmPaymentProviderEnum;
import com.xsy.scm.payment.dao.PaymentMockLedgerDao;
import com.xsy.scm.payment.domain.entity.PaymentMockLedgerEntity;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 本地模拟支付渠道。
 *
 * <p>
 * 它模拟的是渠道这一侧：有自己的一本账（{@link PaymentMockLedgerEntity}）、自己签名、自己决定什么时候回。 业务域那边的验签、幂等与状态机照常执行，mock
 * 无权跳过任何一条业务纪律，否则本地验证就失去意义。
 *
 * <p>
 * 账本用独立事务写的理由是：渠道是另一个系统。如果 mock 的账随业务事务一起回滚，它永远和本地平账， 「渠道扣了钱、本地没记上」这类差异根本测不出来，因此用 {@code REQUIRES_NEW} 如实模拟两边各自记账。
 *
 * <p>
 * 签名用 HMAC-SHA256（密钥来自配置，不进业务规则）。真实渠道实现会换成渠道自己的签名算法与证书， 业务侧代码一行不用改。
 */
@Slf4j
@Component
@Profile("(dev | test) & !pre & !prod")
@RequiredArgsConstructor
public class MockPaymentProvider implements ScmPaymentProvider {

    /** 本地模拟渠道的签名请求头。真实渠道各有自己的头名。 */
    public static final String SIGNATURE_HEADER = "X-Scm-Mock-Signature";

    /** 本地模拟渠道的事件 id 请求头；缺省时回落到报文里的 eventId 字段。 */
    public static final String EVENT_ID_HEADER = "X-Scm-Mock-Event-Id";

    private final PaymentMockLedgerDao paymentMockLedgerDao;

    /**
     * 渠道账本写入口。<b>必须经由这个独立 Bean</b>：{@code REQUIRES_NEW} 只有经过 Spring 代理才生效， 在同类内自调用会让渠道账并入本地业务事务一起回滚。
     */
    private final MockPaymentLedgerRecorder ledgerRecorder;

    /**
     * 模拟渠道的签名密钥。<b>只在这里读</b>：渠道密钥不进业务规则、不进客户端、不落库。
     */
    @Value("${scm.payment.mock.secret:scm-local-mock-secret}")
    private String secret;

    @Override
    public ScmPaymentProviderEnum provider() {
        return ScmPaymentProviderEnum.MOCK;
    }

    @Override
    public IntentResult createIntent(IntentRequest request) {
        ScmPaymentMockScenarioEnum scenario = request.scenario() == null
                ? ScmPaymentMockScenarioEnum.SUCCESS
                : request.scenario();
        String providerTransactionNo = transactionNoOf(request.intentNo());
        String externalIntentId = "MOCK-INTENT-" + request.intentNo();
        return switch (scenario) {
            case SUCCESS -> {
                // 渠道先记账（独立事务），再告诉本地「成功了」
                ledgerRecorder.record(providerTransactionNo, null, "IN", request.amount());
                yield new IntentResult(externalIntentId, providerTransactionNo, Outcome.SUCCEEDED, request.amount(),
                        null, null);
            }
            case FAILURE -> new IntentResult(externalIntentId, providerTransactionNo, Outcome.FAILED, null,
                    "MOCK_DECLINED", "模拟渠道拒付");
            case DELAYED, EXPIRED ->
                // 延迟与过期都不立即记账：等真正成功的那一刻（回调）才入渠道账
                new IntentResult(externalIntentId, providerTransactionNo, Outcome.PENDING, null, null, null);
        };
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        ScmPaymentMockScenarioEnum scenario = request.scenario() == null
                ? ScmPaymentMockScenarioEnum.SUCCESS
                : request.scenario();
        String providerRefundNo = "MOCK-REFUND-" + request.refundNo();
        if (scenario == ScmPaymentMockScenarioEnum.FAILURE) {
            return new RefundResult(providerRefundNo, Outcome.FAILED, null, "MOCK_REFUND_REJECTED", "模拟渠道拒绝退款");
        }
        ledgerRecorder.record(request.providerTransactionNo(), providerRefundNo, "OUT", request.amount());
        return new RefundResult(providerRefundNo, Outcome.SUCCEEDED, request.amount(), null, null);
    }

    /**
     * 验签 + 归一化。
     *
     * <p>
     * 报文格式（本地模拟，字段与真实渠道同构）：
     * {@code {"eventId":"...","eventType":"PAYMENT_SUCCEEDED","providerTransactionNo":"...","amount":"12.0000",
     * "providerRefundNo":null,"bizDate":"2026-10-03"}}。
     *
     * <p>
     * 不抛异常：验签失败也返回一个 {@code signatureVerified = false} 的结果， 让业务侧落一条 REJECTED 事件留证。
     */
    @Override
    public Callback parseCallback(Map<String, String> headers, String rawBody) {
        Map<String, Object> payload = readJson(rawBody);
        if (payload == null) {
            return new Callback(null, null, null, null, null, null, Map.of(), false, "回调报文不是合法 JSON 对象");
        }
        String eventId = firstNonBlank(header(headers, EVENT_ID_HEADER), text(payload.get("eventId")));
        String expected = sign(rawBody);
        String actual = header(headers, SIGNATURE_HEADER);
        boolean verified = actual != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
        String rawEventType = text(payload.get("eventType"));
        ScmPaymentCallbackEventTypeEnum eventType = ScmPaymentCallbackEventTypeEnum.of(rawEventType);
        BigDecimal amount = decimal(payload.get("amount"));
        String providerRefundNo = text(payload.get("providerRefundNo"));
        if (!verified) {
            return new Callback(eventId, eventType, rawEventType, text(payload.get("providerTransactionNo")), amount,
                    providerRefundNo, payload, false, "签名不匹配");
        }
        if (eventType == null) {
            return new Callback(eventId, null, rawEventType, text(payload.get("providerTransactionNo")), amount,
                    providerRefundNo, payload, true, "事件类型不认识");
        }
        return new Callback(eventId, eventType, rawEventType, text(payload.get("providerTransactionNo")), amount,
                providerRefundNo, payload, true, null);
    }

    /**
     * 查单：从渠道账本里找这一笔。
     *
     * <p>
     * 查得到「入」即视为成功；查不到返回 {@code null}（渠道没有这笔）—— 这正是对账要区分的情形之一。
     */
    @Override
    public TransactionResult queryTransaction(String providerTransactionNo) {
        PaymentMockLedgerEntity row = paymentMockLedgerDao.selectInByProviderTransactionNo(providerTransactionNo);
        if (row == null) {
            return null;
        }
        return new TransactionResult(providerTransactionNo, row.getAmount(), Outcome.SUCCEEDED, row.getOccurredAt());
    }

    /**
     * 渠道收款对账明细：按业务日汇总渠道自己的账（<b>只取 IN</b>，退款对账是后续独立一项）。
     */
    @Override
    public Settlement fetchSettlement(LocalDate bizDate) {
        List<PaymentMockLedgerEntity> rows = paymentMockLedgerDao.listByBizDate(bizDate);
        List<SettlementLine> lines = new ArrayList<>(rows.size());
        BigDecimal total = BigDecimal.ZERO;
        for (PaymentMockLedgerEntity row : rows) {
            if (!"IN".equals(row.getDirection())) {
                continue;
            }
            lines.add(new SettlementLine(row.getProviderTransactionNo(), row.getAmount()));
            total = total.add(row.getAmount());
        }
        return new Settlement(bizDate, total, lines.size(), lines);
    }

    /** 供模拟入口构造报文用：把签名算法留在实现里，业务域不需要知道。 */
    public String sign(String rawBody) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("模拟渠道签名失败", e);
        }
    }

    /** 渠道交易号：由本地意图号稳定派生，因此同一意图重发得到同一个渠道号（便于幂等验证）。 */
    private static String transactionNoOf(String intentNo) {
        return "MOCK-TXN-" + intentNo;
    }

    private static String header(Map<String, String> headers, String name) {
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readJson(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        try {
            Object parsed = new com.fasterxml.jackson.databind.ObjectMapper().readValue(rawBody, Map.class);
            return parsed instanceof Map ? (Map<String, Object>) parsed : null;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return null;
        }
    }
}
