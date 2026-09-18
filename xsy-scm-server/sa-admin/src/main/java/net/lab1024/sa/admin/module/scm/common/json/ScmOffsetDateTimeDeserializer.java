package net.lab1024.sa.admin.module.scm.common.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

/**
 * 接受 SCM 展示时间（{@code yyyy-MM-dd HH:mm:ss}）和 ISO-8601 时间。
 * 展示格式按 {@link ScmOffsetDateTimeSerializer#DISPLAY_ZONE} 解读，ISO 输入保留其偏移量。
 * {@code null} 或空白字符串返回 {@code null}；非法日期与非字符串输入拒绝解析。
 */
public class ScmOffsetDateTimeDeserializer extends JsonDeserializer<OffsetDateTime> {

    private static final DateTimeFormatter INPUT_FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

    @Override
    public OffsetDateTime deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        if (parser.hasToken(JsonToken.VALUE_NULL)) {
            return null;
        }
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return (OffsetDateTime) context.handleUnexpectedToken(OffsetDateTime.class, parser);
        }
        String text = parser.getValueAsString();
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.trim();

        try {
            return LocalDateTime.parse(value, INPUT_FORMATTER)
                    .atZone(ScmOffsetDateTimeSerializer.DISPLAY_ZONE)
                    .toOffsetDateTime();
        } catch (DateTimeParseException ignored) {
            // 展示格式不匹配时继续兼容 ISO-8601 请求和持久化结果。
        }

        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw InvalidFormatException.from(parser,
                    "无法解析为 OffsetDateTime，请使用 yyyy-MM-dd HH:mm:ss 或 ISO-8601"
                            + "（例如 2026-09-18T10:51:51+08:00），实际收到：" + value,
                    value, OffsetDateTime.class);
        }
    }
}
