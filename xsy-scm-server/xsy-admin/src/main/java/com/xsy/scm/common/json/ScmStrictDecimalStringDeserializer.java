package com.xsy.scm.common.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;

import java.io.IOException;

/**
 * 金额和数量只接受 JSON 字符串，不接受数字字面量，避免隐式转换造成精度或格式不一致。
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
        throw JsonMappingException.from(parser, "SCM 定点数值必须是 JSON 字符串，实际为 " + token);
    }
}
