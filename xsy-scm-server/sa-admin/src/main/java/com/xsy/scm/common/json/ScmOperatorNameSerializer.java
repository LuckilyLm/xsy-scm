package com.xsy.scm.common.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.function.UnaryOperator;

/** 将操作人标识转换为可读姓名；无法识别时保留原值，避免历史记录丢失信息。 */
public class ScmOperatorNameSerializer extends JsonSerializer<String> {

    /** 默认恒等：解析器尚未绑定（如纯单元测试）时保持原值，不改变任何行为。 */
    private static volatile UnaryOperator<String> resolver = UnaryOperator.identity();

    /** 由 {@code ScmOperatorNameResolver} 在启动时调用。 */
    static void bind(UnaryOperator<String> operatorNameResolver) {
        resolver = operatorNameResolver;
    }

    /**
     * 把单个原始值换成人名；认不出形状时原样返回。
     *
     * <p>
     * 给 {@link ScmOperatorSnapshotSerializer} 复用：审计快照里的操作人藏在 Map 的键上，走不了字段注解。
     */
    static String resolve(String value) {
        return value == null ? null : resolver.apply(value);
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        gen.writeString(resolver.apply(value));
    }
}
