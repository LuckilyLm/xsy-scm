package com.xsy.scm.common.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 操作人字段的展示口径：只有 {@code userType:userId} 形状才换人名，其它值一律不动。
 *
 * <p>
 * 用假的解析器直接绑定，不依赖员工表 —— 这里要守的是「什么该换、什么不该换」这条边界，
 * 以及快照里按「键名」递归替换的行为。
 */
class ScmOperatorNameSerializerTest {

    private static final String EMPLOYEE_ONE = "1:1";

    /** 装着快照字段的壳，只为让注解生效，验证真实序列化路径。 */
    private record SnapshotHolder(
            @JsonSerialize(using = ScmOperatorSnapshotSerializer.class) Map<String, Object> data) {
    }

    /** 装上假解析器：只认识 1:1；跑完恢复，避免影响同 JVM 的其它用例。 */
    private void withFakeResolver(Runnable body) {
        ScmOperatorNameSerializer.bind(raw -> EMPLOYEE_ONE.equals(raw) ? "系统管理员" : raw);
        try {
            body.run();
        } finally {
            ScmOperatorNameSerializer.bind(UnaryOperator.identity());
        }
    }

    @Test
    @DisplayName("只认管理端员工形状；真实人名、非员工 userType、坏数据一律返回 null")
    void onlyAdminEmployeeShapeIsParsed() {
        assertThat(ScmOperatorNameResolver.adminEmployeeId(EMPLOYEE_ONE)).isEqualTo(1L);
        assertThat(ScmOperatorNameResolver.adminEmployeeId("1:17")).isEqualTo(17L);

        assertThat(ScmOperatorNameResolver.adminEmployeeId("系统管理员")).isNull();
        // 其它 userType 的 id 与员工 id 不在同一空间，拿员工表查会张冠李戴
        assertThat(ScmOperatorNameResolver.adminEmployeeId("2:1")).isNull();
        assertThat(ScmOperatorNameResolver.adminEmployeeId("1:abc")).isNull();
        assertThat(ScmOperatorNameResolver.adminEmployeeId("1")).isNull();
        assertThat(ScmOperatorNameResolver.adminEmployeeId("")).isNull();
        assertThat(ScmOperatorNameResolver.adminEmployeeId(null)).isNull();
    }

    @Test
    @DisplayName("字段序列化：认得的换人名，认不得的原样输出")
    void serializesKnownOperatorAndKeepsOthers() {
        withFakeResolver(() -> {
            assertThat(ScmOperatorNameSerializer.resolve(EMPLOYEE_ONE)).isEqualTo("系统管理员");
            // 员工已删 / 查不到 → 回落原值，不吞数据
            assertThat(ScmOperatorNameSerializer.resolve("1:999")).isEqualTo("1:999");
            assertThat(ScmOperatorNameSerializer.resolve("李文博")).isEqualTo("李文博");
            assertThat(ScmOperatorNameSerializer.resolve(null)).isNull();
        });
    }

    @Test
    @DisplayName("审计快照：顶层与嵌套的操作人键都换人名，其它键不动")
    void snapshotRewritesOperatorKeysAtAnyDepth() throws Exception {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("status", "CANCELLED");
        snapshot.put("createdBy", EMPLOYEE_ONE);
        snapshot.put("address", new LinkedHashMap<>(Map.of("createdBy", EMPLOYEE_ONE, "receiverName", "收货部")));
        // 列表里的元素不在递归范围内：这是有意划的边界，避免把任意结构都走一遍
        snapshot.put("items", List.of(Map.of("operator", EMPLOYEE_ONE)));

        withFakeResolver(() -> {
            String json;
            try {
                json = new ObjectMapper().writeValueAsString(new SnapshotHolder(snapshot));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            assertThat(json).contains("\"createdBy\":\"系统管理员\"");
            assertThat(json).contains("\"status\":\"CANCELLED\"");
            assertThat(json).contains("\"receiverName\":\"收货部\"");
            assertThat(json).contains("\"operator\":\"1:1\"");
        });
    }
}
