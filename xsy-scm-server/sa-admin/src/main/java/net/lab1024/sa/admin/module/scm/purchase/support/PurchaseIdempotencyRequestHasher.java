package net.lab1024.sa.admin.module.scm.purchase.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/**
 * 规范化请求哈希（幂等重放判定）。
 *
 * <p>设计依据：W5 Target Design §7.11 —— 「复用 {@code OrderIdempotencyRequestHasher} 的纪律
 * （键排序 / 数字 {@code stripTrailingZeros} / 数字字符串归一），复制为
 * {@code PurchaseIdempotencyRequestHasher}」。
 *
 * <p><b>为什么复制而不是复用 W4 的类</b>：与 {@code PurchaseJsonbTypeHandler} 同一理由 ——
 * 复用会让 {@code purchase} 域在**支撑设施**层面依赖 {@code order} 域。W4 的幂等实现已验收冻结，
 * 复制纪律比引入跨域耦合更便宜。
 *
 * <p>规范化规则（三条，缺一不可，否则「同 key 同内容」会被误判为冲突）：
 * <ol>
 *   <li><b>键排序</b>：对象键按字典序重建，Map 的迭代顺序不影响哈希；</li>
 *   <li><b>数字归一</b>：JSON 数字 {@code stripTrailingZeros}（{@code 1.5000} → {@code 1.5}）；</li>
 *   <li><b>数字字符串归一</b>：形如数字的**字符串**同样 {@code stripTrailingZeros}，
 *       因为 W5 的定点数字段（数量 / 单价）在请求体里就是字符串
 *       （{@code "1.5000"} 与 {@code "1.5"} 必须视为同一请求）。</li>
 * </ol>
 *
 * <p><b>{@code null} 与 {@code "0.0000"} 必须哈希不同</b>：W5 的三态语义要求
 * 「无值」与「值为零」是两个不同请求，绝不能归一化成同一个键。
 */
public final class PurchaseIdempotencyRequestHasher {

    private final ObjectMapper objectMapper;

    public PurchaseIdempotencyRequestHasher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    /**
     * 计算 SHA-256 十六进制摘要。
     */
    public String hash(Object request) {
        try {
            JsonNode tree = objectMapper.valueToTree(request);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical(tree).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | JsonProcessingException exception) {
            throw new IllegalStateException("无法计算幂等请求哈希", exception);
        }
    }

    private String canonical(JsonNode node) throws JsonProcessingException {
        if (node.isObject()) {
            var sorted = objectMapper.createObjectNode();
            node.properties().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> sorted.set(e.getKey(), canonicalNode(e.getValue())));
            return objectMapper.writeValueAsString(sorted);
        }
        return objectMapper.writeValueAsString(canonicalNode(node));
    }

    private JsonNode canonicalNode(JsonNode node) {
        if (node.isObject()) {
            var sorted = objectMapper.createObjectNode();
            node.properties().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> sorted.set(e.getKey(), canonicalNode(e.getValue())));
            return sorted;
        }
        if (node.isArray()) {
            var result = objectMapper.createArrayNode();
            node.forEach(v -> result.add(canonicalNode(v)));
            return result;
        }
        if (node.isNumber()) {
            return objectMapper.getNodeFactory().numberNode(node.decimalValue().stripTrailingZeros());
        }
        if (node.isTextual() && node.textValue().matches("-?\\d+(\\.\\d+)?")) {
            try {
                return objectMapper.getNodeFactory()
                        .textNode(new BigDecimal(node.textValue()).stripTrailingZeros().toPlainString());
            } catch (NumberFormatException ignored) {
                // 不是合法数字（例如超出 BigDecimal 容量）：按原样字符串处理
            }
        }
        return node;
    }
}
