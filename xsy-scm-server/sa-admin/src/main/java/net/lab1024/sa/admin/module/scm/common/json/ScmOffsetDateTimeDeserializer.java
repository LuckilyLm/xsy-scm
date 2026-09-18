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
 * SCM 时间字段的反序列化器 —— {@link ScmOffsetDateTimeSerializer} 的**对称件**。
 *
 * <p><b>为什么必须有它</b>：只注册序列化器、不注册反序列化器，会让
 * 「API 返回什么，客户端就该能把什么送回来」这条最基本的往返契约断掉。具体地：
 * <ul>
 *   <li>序列化输出 {@code "2026-09-18 10:51:51"}（{@code yyyy-MM-dd HH:mm:ss}，秒级、北京时间）；</li>
 *   <li>反序列化侧若仍是 Jackson 默认的 ISO-8601，同一份值再送回来就解析失败。</li>
 * </ul>
 * 这条不对称在 2026-09-17 的提交 {@code 48134bf}（统一时间显示格式）里被引入，
 * 直接后果是**所有含 {@code OffsetDateTime} 的 VO 在幂等重放时抛异常**
 * （{@code PurchaseIdempotencyService.replay} 会把库里的 JSON 还原成 VO）——
 * 见 W6 验收报告中的「既有回归」条目。
 *
 * <p><b>接受的两种形状</b>（按顺序尝试）：
 * <pre>
 *   "2026-09-18 10:51:51"            -> 2026-09-18T10:51:51+08:00（按展示时区 Asia/Shanghai 解释）
 *   "2026-09-18T10:51:51+08:00"      -> 原样（ISO-8601，请求体的既有形状，必须继续接受）
 *   "2026-09-18T02:51:51Z"           -> 原样（ISO-8601 的 UTC 写法）
 *   null / "" / "  "                 -> null（保留「无值」语义）
 * </pre>
 *
 * <p><b>为什么展示格式按 Asia/Shanghai 解释</b>：该格式由
 * {@link ScmOffsetDateTimeSerializer} 用 {@code DISPLAY_ZONE} 生成，
 * 解读时用同一个时区才可能还原成正确的时刻。这也是「不带时区后缀的时间串」唯一合理的读法 ——
 * 用「服务器默认时区」或「客户端本地时区」都会让同一份数据在不同机器上落到不同时刻。
 *
 * <p>非法输入**不静默吞掉**：抛出 {@link InvalidFormatException}（Jackson 的 400 语义），
 * 消息里给出两种可接受形状，而不是让调用方对着一个裸的解析异常猜。
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

        // 1) 展示格式（本域序列化器的输出）：无时区后缀，按展示时区解释
        try {
            return LocalDateTime.parse(value, INPUT_FORMATTER)
                    .atZone(ScmOffsetDateTimeSerializer.DISPLAY_ZONE)
                    .toOffsetDateTime();
        } catch (DateTimeParseException ignored) {
            // 不是展示格式 → 试 ISO-8601
        }

        // 2) ISO-8601（请求体的既有形状）
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
