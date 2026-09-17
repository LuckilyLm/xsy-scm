package net.lab1024.sa.admin.module.scm.purchase;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.lab1024.sa.admin.module.scm.purchase.support.PurchaseIdempotencyRequestHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 幂等请求哈希契约测试（W5 Target Design §11.1，5 例；§7.11）。
 *
 * <p>这三条规范化规则决定了「同 key 同内容 → 重放」与「同 key 异内容 → 40990」的边界，
 * 任何一条缺失都会造成**误判**：
 * <ul>
 *   <li>缺键排序 → 同一请求因 Map 迭代顺序不同被判定为内容冲突；</li>
 *   <li>缺数字归一 → `1.5000` 与 `1.5` 被判为两个不同请求；</li>
 *   <li>缺数字字符串归一 → W5 的定点字段（请求体里就是字符串）无法重放。</li>
 * </ul>
 *
 * <p>反向边界同样重要：**{@code null} 与 {@code "0.0000"} 必须哈希不同**，
 * 否则「无值」与「值为零」会被当成同一个请求（W5 三态语义）。
 */
class PurchaseIdempotencyRequestHasherTest {

    private final PurchaseIdempotencyRequestHasher hasher =
            new PurchaseIdempotencyRequestHasher(new ObjectMapper());

    @Test
    @DisplayName("键排序：对象键顺序不影响哈希；但数组顺序必须影响（分配顺序是请求的一部分）")
    void keyOrderDoesNotMatterButArrayOrderDoes() {
        Map<String, Object> left = new LinkedHashMap<>();
        left.put("z", 1);
        left.put("a", List.of(Map.of("p", "1.5000", "r", "9.0000"), Map.of("q", "2.0000")));
        left.put("m", Map.of("nested", Map.of("y", "x", "b", "a")));

        Map<String, Object> right = new LinkedHashMap<>();
        right.put("m", Map.of("nested", Map.of("b", "a", "y", "x")));
        right.put("a", List.of(Map.of("r", "9.0000", "p", "1.5000"), Map.of("q", "2.0000")));
        right.put("z", 1);

        assertThat(hasher.hash(left)).isEqualTo(hasher.hash(right));

        // 数组是**有序**的：allocations 的先后不可归一，否则「换序的同一组分配」会被误判为重放
        assertThat(hasher.hash(Map.of("a", List.of(Map.of("p", "1.5000"), Map.of("q", "2.0000")))))
                .isNotEqualTo(hasher.hash(Map.of("a", List.of(Map.of("q", "2.0000"), Map.of("p", "1.5000")))));
    }

    @Test
    @DisplayName("数字归一：JSON 数字 stripTrailingZeros（1.5000 == 1.5，100.0000 == 100）")
    void numericValuesAreStripped() {
        assertThat(hasher.hash(Map.of("q", new BigDecimal("1.5000"))))
                .isEqualTo(hasher.hash(Map.of("q", new BigDecimal("1.5"))));
        assertThat(hasher.hash(Map.of("q", new BigDecimal("100.0000"))))
                .isEqualTo(hasher.hash(Map.of("q", 100)));
    }

    @Test
    @DisplayName("数字字符串归一：W5 的定点字符串字段必须可重放（\"1.5000\" == \"1.5\"）")
    void numericStringsAreNormalized() {
        assertThat(hasher.hash(Map.of("quantity", "1.5000")))
                .isEqualTo(hasher.hash(Map.of("quantity", "1.5")));
        assertThat(hasher.hash(Map.of("price", "0.0000")))
                .isEqualTo(hasher.hash(Map.of("price", "0")));
        // 非数字字符串原样保留，不做任何改写
        assertThat(hasher.hash(Map.of("reason", "供应商缺货")))
                .isNotEqualTo(hasher.hash(Map.of("reason", "供应商缺货 ")));
    }

    @Test
    @DisplayName("三态语义：null 与 \"0.0000\" 必须哈希不同")
    void nullIsNotZero() {
        assertThat(hasher.hash(Map.of("price", "0.0000")))
                .isNotEqualTo(hasher.hash(Collections.singletonMap("price", null)));
        // 嵌套位置同样成立：{allocations:[{demandVersion:null}]} ≠ {allocations:[{demandVersion:0}]}
        assertThat(hasher.hash(Map.of("allocations",
                List.of(Collections.singletonMap("demandVersion", null)))))
                .isNotEqualTo(hasher.hash(Map.of("allocations", List.of(Map.of("demandVersion", 0)))));
    }

    @Test
    @DisplayName("确定性：同一请求多次哈希结果稳定，且是 64 位十六进制 SHA-256")
    void hashIsStableAndSha256() {
        Map<String, Object> request = Map.of("supplierId", 1, "items", List.of(Map.of("skuId", 100, "quantity", "3.0000")));
        String first = hasher.hash(request);
        assertThat(first).isEqualTo(hasher.hash(request));
        assertThat(first).matches("[0-9a-f]{64}");

        // 内容变化必须改变哈希
        assertThat(hasher.hash(Map.of("supplierId", 2, "items",
                List.of(Map.of("skuId", 100, "quantity", "3.0000"))))).isNotEqualTo(first);
    }
}
