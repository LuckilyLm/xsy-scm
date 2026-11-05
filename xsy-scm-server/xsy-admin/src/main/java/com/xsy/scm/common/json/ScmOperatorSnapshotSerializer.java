package com.xsy.scm.common.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * 操作日志里的操作人字段统一转成人名，嵌套对象也处理。递归有深度限制，不改原始 JSONB 数据。
 */
public class ScmOperatorSnapshotSerializer extends JsonSerializer<Map<String, Object>> {

    /** 审计内容里可能承载操作人的键名。 */
    private static final Set<String> OPERATOR_KEYS = Set.of("operator", "createdBy", "updatedBy", "printedBy");

    /** 递归深度上限；正常内容只有一层嵌套。 */
    private static final int MAX_DEPTH = 6;

    @Override
    public void serialize(Map<String, Object> value, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
        writeMap(value, gen, serializers, 0);
    }

    private void writeMap(Map<?, ?> map, JsonGenerator gen, SerializerProvider serializers, int depth)
        throws IOException {
        gen.writeStartObject();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            gen.writeFieldName(String.valueOf(entry.getKey()));
            writeValue(entry.getKey(), entry.getValue(), gen, serializers, depth);
        }
        gen.writeEndObject();
    }

    private void writeValue(Object key, Object value, JsonGenerator gen, SerializerProvider serializers, int depth)
        throws IOException {
        if (value instanceof Map<?, ?> nested && depth < MAX_DEPTH) {
            writeMap(nested, gen, serializers, depth + 1);
            return;
        }
        if (value instanceof String text && OPERATOR_KEYS.contains(String.valueOf(key))) {
            gen.writeString(ScmOperatorNameSerializer.resolve(text));
            return;
        }
        serializers.defaultSerializeValue(value, gen);
    }
}
