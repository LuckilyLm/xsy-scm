package com.xsy.scm.common.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;

import java.io.IOException;

/**
 * SCM 定点数字段的反序列化守卫：只接受 JSON <b>字符串</b>。
 *
 * <p>
 * Jackson 默认会把 JSON 数字 {@code 12.34} 静默强转成 {@code String} 字段的 {@code "12.34"}，这会让
 * 「数量与金额一律以字符串传输」的契约在传输层就失效。数字字面量在这里直接拒绝，由 Jackson 抛 {@link JsonMappingException} → Spring 包装 → SmartAdmin
 * {@code GlobalExceptionHandler} 返回 30001。
 *
 * <p>
 * 与 {@link ScmFixedScale4Serializer} 成对：入站只收字符串，出站只发字符串。
 *
 * @see com.xsy.scm.common.util.ScmDecimalStrings
 */
public class ScmStrictDecimalStringDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        if (token == JsonToken.VALUE_STRING) {
            return parser.getText();
        }
        throw JsonMappingException.from(parser, "SCM fixed-point value must be a JSON string, but got " + token);
    }
}
