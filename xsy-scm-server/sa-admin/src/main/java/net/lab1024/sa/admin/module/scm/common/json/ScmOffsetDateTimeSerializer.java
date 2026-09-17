package net.lab1024.sa.admin.module.scm.common.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * SCM 时间字段的序列化器：{@code yyyy-MM-dd HH:mm:ss}（秒级，带时区换算）。
 *
 * <p><b>为什么必须显式注册：</b>SmartAdmin 的 {@code JsonConfig} 只给
 * {@link java.time.LocalDate} 与 {@link java.time.LocalDateTime} 注册了
 * {@code DatePattern.NORM_DATETIME_FORMAT} 序列化器，<b>没有覆盖 {@link OffsetDateTime}</b>。
 * 而 SCM 域的 VO 全部使用 {@code OffsetDateTime}（共 138 个字段），
 * 于是走 Jackson 默认的 ISO-8601 输出：
 * <pre>
 *   2026-09-17T13:50:57.394795+08:00
 * </pre>
 * 前端直接把它渲染出来，表现为「日期与时间粘连、带 6 位微秒和时区后缀」，
 * 既难读又与列表其他列风格不一致。
 *
 * <p><b>为什么 {@code spring.jackson.date-format} 不管用：</b>
 * 该配置项只作用于 {@link java.util.Date}，对 {@code java.time.*} 无效。
 * 这也是本项目 {@code sa-base.yaml} 里已经写了
 * {@code date-format: yyyy-MM-dd HH:mm:ss}、LocalDateTime 也正常，
 * 唯独 SCM 的时间仍然难看的原因。
 *
 * <p><b>本序列化器的契约：</b>
 * <pre>
 *   null                        -> JSON null   （保留「无值」语义，前端显示 —）
 *   2026-09-17T13:50:57.394795+08:00 -> "2026-09-17 13:50:57"
 *   2026-09-17T05:50:57Z             -> "2026-09-17 13:50:57"（按 +08 换算）
 * </pre>
 *
 * <p>时间统一换算到 {@code Asia/Shanghai}（即 {@code +08:00}）后输出，
 * 与 {@code sa-base.yaml} 的 {@code time-zone: GMT+8} 约定一致，
 * 避免同一份数据在不同时区客户端上显示成不同时刻。
 *
 * @see net.lab1024.sa.base.config.JsonConfig
 */
public class ScmOffsetDateTimeSerializer extends JsonSerializer<OffsetDateTime> {

    /**
     * 输出格式：秒级，不带毫秒与时区后缀。
     */
    public static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 展示时区（中国标准时间）。
     */
    public static final java.time.ZoneId DISPLAY_ZONE = java.time.ZoneId.of("Asia/Shanghai");

    @Override
    public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        if (value == null) {
            // 关键：保留 null，前端据此显示「—」，而不是伪装成一个真实时刻
            gen.writeNull();
            return;
        }
        // 先转成展示时区再格式化，保证输出的是北京时间
        gen.writeString(value.atZoneSameInstant(DISPLAY_ZONE).format(FORMATTER));
    }
}
