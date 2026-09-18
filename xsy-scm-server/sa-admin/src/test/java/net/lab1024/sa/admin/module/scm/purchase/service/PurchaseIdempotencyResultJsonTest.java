package net.lab1024.sa.admin.module.scm.purchase.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 幂等结果的持久化编解码（W6 修正 48134bf 引入的回归，见 {@code PurchaseIdempotencyService#RESULT_JSON}）。
 *
 * <p>被测的是「写出去再读回来是否**无损**」，因为幂等重放的契约是
 * 「返回首次的**真实**结果」而不是「返回一个大致相同的对象」。
 *
 * <p><b>为什么测试放在 {@code ...purchase.service} 子包</b>：被测的 mapper 刻意保持包私有
 * （它是这个服务的内部实现细节，不是给外部用的 API）。
 */
@DisplayName("W6 幂等结果持久化编解码（单元）")
class PurchaseIdempotencyResultJsonTest {

    /**
     * 覆盖幂等结果里会出现的全部类型形状：带时区时间 / null 时间 / 字符串 / 定点数 / 布尔。
     *
     * <p>用 record 而不是 Map：Jackson 对 record 走「构造器反序列化」，
     * 与 VO 的反序列化路径最接近，能真实暴露「字段名对不上」「类型不可构造」这类问题。
     */
    private record Payload(OffsetDateTime confirmedAt, OffsetDateTime emptyAt, String receiptNo,
                           BigDecimal quantity, Boolean deleted) {
    }

    private static Map<String, Object> store(Object payload) {
        return (Map<String, Object>) PurchaseIdempotencyService.RESULT_JSON
                .convertValue(payload, Object.class);
    }

    private static Payload restore(Map<String, Object> stored) {
        return PurchaseIdempotencyService.RESULT_JSON.convertValue(stored, Payload.class);
    }

    @Test
    @DisplayName("往返无损：微秒精度、null、定点数、布尔全部原样还原")
    void roundTripIsLossless() {
        OffsetDateTime exact = OffsetDateTime.parse("2026-09-18T10:51:51.394795+08:00");
        Payload original = new Payload(exact, null, "PR20260918001",
                new BigDecimal("10.0000"), Boolean.FALSE);

        Payload restored = restore(store(original));

        // 关键：微秒必须保留。展示格式（秒级）会让这里失败 —— 那正是被修正的缺陷。
        assertThat(restored.confirmedAt().toInstant()).isEqualTo(exact.toInstant());
        assertThat(restored.emptyAt()).isNull();
        assertThat(restored.receiptNo()).isEqualTo("PR20260918001");
        assertThat(restored.quantity()).isEqualByComparingTo("10.0000");
        assertThat(restored.deleted()).isFalse();
    }

    @Test
    @DisplayName("兼容回归期间的旧数据：展示格式 yyyy-MM-dd HH:mm:ss 仍可重放")
    void legacyDisplayFormatStillReplays() {
        // 只注册序列化器的那段时间里，首次执行成功的请求写下的就是这种形状
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("confirmedAt", "2026-09-18 10:51:51");
        legacy.put("emptyAt", null);
        legacy.put("receiptNo", "PR20260918002");
        legacy.put("quantity", "3.0000");
        legacy.put("deleted", Boolean.FALSE);

        Payload restored = restore(legacy);

        // 展示格式不带时区后缀，按展示时区（Asia/Shanghai）解释 → 2026-09-18T10:51:51+08:00
        assertThat(restored.confirmedAt().toInstant())
                .isEqualTo(OffsetDateTime.parse("2026-09-18T10:51:51+08:00").toInstant());
    }

    @Test
    @DisplayName("ISO-8601 两种写法都接受（+08:00 与 Z），且未知字段被忽略")
    void acceptsIsoFormsAndIgnoresUnknownProperties() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("confirmedAt", "2026-09-18T02:51:51Z");
        payload.put("receiptNo", "PR20260918003");
        payload.put("quantity", "1.0000");
        payload.put("deleted", Boolean.TRUE);
        // 未知字段：VO 后来删掉某个字段时，库里已有的结果不应因此变得不可重放
        payload.put("fieldRemovedInALaterWave", "whatever");

        Payload restored = restore(payload);

        assertThat(restored.confirmedAt().toInstant())
                .isEqualTo(OffsetDateTime.parse("2026-09-18T02:51:51Z").toInstant());
        assertThat(restored.deleted()).isTrue();
    }

    @Test
    @DisplayName("非法时间串不静默吞掉：抛异常，而不是悄悄变成 null")
    void invalidTimestampIsRejected() {
        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("confirmedAt", "2026/09/18 10:51");
        bad.put("receiptNo", "PR20260918004");
        bad.put("quantity", "1.0000");
        bad.put("deleted", Boolean.FALSE);

        // 静默变 null 会让「重放返回首次的真实结果」变成「返回一个悄悄丢了时间的结果」，
        // 那比直接报错危险得多
        assertThatThrownBy(() -> restore(bad)).isInstanceOf(IllegalArgumentException.class);
    }
}
