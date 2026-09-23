package net.lab1024.sa.base.module.support.operatelog.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 8 §10.2：切面写库前脱敏的口径测试（纯单测，不起 Spring）。
 *
 * <p>重点不是「遮住了多少」，而是遮住敏感键的同时<b>不能</b>碰业务对象键：
 * 操作日志的商品 / 客户精确筛选直接对 {@code param} 文本做 STRPOS，
 * 一旦脱敏改写 {@code "spuId":12,} 这类片段，Wave 8 的结构化筛选会静默失效。
 */
@DisplayName("操作日志参数脱敏")
class OperateLogParamMaskTest {

    @Test
    @DisplayName("敏感键值被遮蔽，大小写与嵌套层级都覆盖")
    void masksSecretKeysIncludingNestedAndCaseInsensitive() {
        String param = """
                [{"editForm":{"password":"P@ssw0rd","NickName":"小明"},\
                "authorization":"Bearer abc","session":{"refreshToken":"rt-1","deviceId":"d-9"}}]""";

        String masked = OperateLogParamMask.mask(param);

        assertThat(masked).doesNotContain("P@ssw0rd").doesNotContain("Bearer abc").doesNotContain("rt-1");
        assertThat(masked).contains("\"password\":\"******\"")
                .contains("\"authorization\":\"******\"")
                .contains("\"refreshToken\":\"******\"");
        // 非敏感键保持原值与原顺序
        assertThat(masked).contains("\"NickName\":\"小明\"").contains("\"deviceId\":\"d-9\"");
    }

    @Test
    @DisplayName("容器键自身命中时整棵子树遮蔽，不留明文子节点")
    void masksWholeSubtreeWhenContainerKeyNameMatches() {
        String param = "[{\"token\":{\"accessKey\":\"AK-1\",\"expireAt\":\"2099-01-01\"}}]";

        assertThat(OperateLogParamMask.mask(param)).doesNotContain("AK-1").doesNotContain("2099-01-01");
    }

    @Test
    @DisplayName("业务对象键与筛选口径不受影响")
    void keepsBusinessObjectKeysIntact() {
        String param = "[{\"spuId\":12,\"customerId\":345,\"routeId\":7}]";

        assertThat(OperateLogParamMask.mask(param)).isEqualTo(param);

        // 发生遮蔽时要重新序列化，此处钉住列表筛选依赖的精确边界片段
        String mixed = "[{\"spuId\":12,\"customerId\":9,\"password\":\"P@ssw0rd\"}]";
        assertThat(OperateLogParamMask.mask(mixed))
                .contains("\"spuId\":12,")
                .contains("\"customerId\":9,")
                .doesNotContain("P@ssw0rd");
    }

    @Test
    @DisplayName("不含敏感键时返回同一份文本，不重排 JSON")
    void returnsIdenticalTextWhenNothingToMask() {
        String param = "[{\"customerId\":9,\"remark\":\"a\",\"items\":[{\"skuId\":3,\"qty\":2}]}]";

        assertThat(OperateLogParamMask.mask(param)).isSameAs(param);
    }

    @Test
    @DisplayName("非 JSON 脏值与空值原样放行，不吞掉整条参数日志")
    void passesThroughNonJsonInput() {
        assertThat(OperateLogParamMask.mask(null)).isNull();
        assertThat(OperateLogParamMask.mask("")).isEmpty();
        assertThat(OperateLogParamMask.mask("plain text")).isEqualTo("plain text");
        // 截断 / 脏 JSON：解析失败留给前端第二层兜底
        assertThat(OperateLogParamMask.mask("{\"password\":\"x\"")).isEqualTo("{\"password\":\"x\"");
    }
}
