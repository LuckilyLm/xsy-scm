package com.xsy.scm.common.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.function.UnaryOperator;

/**
 * 操作人字段的序列化器：把 {@code userType:userId}（形如 {@code 1:1}）换成人名。
 *
 * <p>
 * 原始值来自 {@code ScmOperator.current()}，它是登录标识而不是人名，直接落到界面/导出/打印上没人看得懂。 同一个形状散落在 30 多个 VO 的 {@code operator} /
 * {@code createdBy} / {@code updatedBy} / {@code printedBy} 上，逐个 service 补名字既重复又容易漏， 因此按字段注解统一收口。
 *
 * <p>
 * 两条边界：
 * <ul>
 * <li>只认 {@code 数字:数字} 这个形状，认不出的一律原样输出 —— 真实人名、空串、其它标识都不会被误改；</li>
 * <li>解析不到 id（员工已删）时回落成原始值，不吞数据、不显示空白。</li>
 * </ul>
 *
 * <p>
 * 序列化器由 Jackson 自己实例化，拿不到 Spring 容器，所以解析能力由 {@code ScmOperatorNameResolver} 在启动时绑定进来。
 */
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
