package com.xsy.scm.common.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * SCM 时间字段的序列化器：{@code yyyy-MM-dd HH:mm:ss}（秒级，统一换算到 {@code Asia/Shanghai}）。
 *
 * <p>
 * null → JSON null（保留「无值」语义，前端显示 —）；带时区的输入按 +08:00 换算后输出，避免同一份数据在不同时区客户端上显示成不同时刻。
 *
 * <p>
 * <b>必须显式注册</b>：SmartAdmin 的 {@code JsonConfig} 只给 {@code LocalDate} / {@code LocalDateTime} 注册了格式，未覆盖
 * {@code OffsetDateTime}，而 SCM 的 VO 全部使用它； {@code spring.jackson.date-format} 也只作用于 {@code java.util.Date}。
 *
 * @see net.lab1024.sa.base.config.JsonConfig
 */
public class ScmOffsetDateTimeSerializer extends JsonSerializer<OffsetDateTime> {

    /**
     * 输出格式：秒级，不带毫秒与时区后缀。
     */
    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 展示时区（中国标准时间）。
     */
    public static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");

    @Override
    public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            // 关键：保留 null，前端据此显示「—」，而不是伪装成一个真实时刻
            gen.writeNull();
            return;
        }
        // 先转成展示时区再格式化，保证输出的是北京时间
        gen.writeString(value.atZoneSameInstant(DISPLAY_ZONE).format(FORMATTER));
    }
}
