package com.xsy.scm.common.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 日期时间统一转为北京时间并输出 yyyy-MM-dd HH:mm:ss，空值保持为 null。
 */
public class ScmOffsetDateTimeSerializer extends JsonSerializer<OffsetDateTime> {

    /** 输出格式：秒级，不带毫秒与时区后缀。 */
    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 展示时区（中国标准时间）。 */
    public static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");

    @Override
    public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            // 保持 null，前端显示「—」，别伪装成真实时间
            gen.writeNull();
            return;
        }
        // 先转成北京时间再格式化
        gen.writeString(value.atZoneSameInstant(DISPLAY_ZONE).format(FORMATTER));
    }
}
