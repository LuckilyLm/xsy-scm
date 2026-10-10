package com.xsy.scm.common.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * 审计快照（{@code beforeData} / {@code afterData}）的序列化器：把快照里的操作人也换成人名。
 *
 * <p>
 * 快照是从 JSONB 列读回来的 {@code Map}，操作人藏在 Map 的键上而不是字段上， {@link ScmOperatorNameSerializer} 那种按字段注解的方式够不着，所以给这两个字段单独挂一个 Map
 * 序列化器。
 *
 * <p>
 * 递归处理嵌套对象：地址快照这类子对象里同样带 {@code createdBy}。深度设上限，避免异常数据把栈打穿。 前后两份快照用同一套规则转换，前端「变更前后」的比对结果不受影响。
 */
public class ScmOperatorSnapshotSerializer extends JsonSerializer<Map<String, Object>> {

    /** 快照里可能承载操作人的键名。 */
    private static final Set<String> OPERATOR_KEYS = Set.of("operator", "createdBy", "updatedBy", "printedBy");

    /** 递归深度上限；正常快照只有一层嵌套。 */
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
