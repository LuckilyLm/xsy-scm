package net.lab1024.sa.admin.module.scm.common.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;

import java.io.IOException;

/**
 * SCM 定点数字段的反序列化守卫：只接受 JSON <b>字符串</b>。
 *
 * <p><b>为什么需要它：</b>Jackson 默认会把 JSON 数字 {@code 12.34} 静默强转成 {@code String} 字段的
 * {@code "12.34"}。这看起来无害，但它让「数量与金额一律以字符串传输」的契约在传输层就失效了——
 * 客户端可以继续发送浮点数，而浮点数在序列化阶段已经可能丢精度（例如
 * {@code 0.1 + 0.2} 类问题、或超过 double 精度的 18 位整数）。等到真正出问题时，
 * 责任已经无法定位在服务端。
 *
 * <p>因此 SCM 的定点数字段显式声明本反序列化器：数字字面量直接拒绝，由 Jackson 抛出
 * {@link JsonMappingException} → Spring 包装为 {@code HttpMessageNotReadableException} →
 * 交给 SmartAdmin {@code GlobalExceptionHandler} 返回 30001（参数JSON格式错误）。
 * SCM <b>不</b>额外接管该异常，符合「Backend Infrastructure = SmartAdmin Native First」。
 *
 * <p>与 {@link ScmFixedScale4Serializer} 成对：入站只收字符串，出站只发字符串。
 *
 * @see net.lab1024.sa.admin.module.scm.common.util.ScmDecimalStrings
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
        throw JsonMappingException.from(parser,
                "SCM fixed-point value must be a JSON string, but got " + token);
    }
}
