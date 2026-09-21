package net.lab1024.sa.admin.module.scm.common.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.base.common.json.serializer.BigDecimalNullZeroSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCM null / UNPRICED 语义门禁测试。
 *
 * <p>这是进入 Product Pilot 的前置条件（W0 额外确认项 1）：
 * SmartAdmin 的 {@link BigDecimalNullZeroSerializer} 会把 {@code null} 序列化为 {@code 0}，
 * 与 SCM「缺价 = UNPRICED（值为 null）」的业务语义直接冲突，必须保证它不会污染 SCM。
 *
 * <p>本测试锁定三件事：
 * <ol>
 *   <li>{@link ScmFixedScale4Serializer} 的契约：null 保持 null，0 为 "0.0000"，两者严格可区分；</li>
 *   <li>该契约在真实 Jackson 序列化结果上成立（不是只看序列化器内部逻辑）；</li>
 *   <li>SCM 包内不存在任何使用 {@code BigDecimalNullZeroSerializer} 的类或字段（防回归）。</li>
 * </ol>
 */
class ScmBigDecimalNullSemanticsTest {

    /**
     * SCM 业务代码根包；任何新增 SCM 代码都必须落在这个包下。
     */
    private static final String SCM_ROOT_PACKAGE = "net.lab1024.sa.admin.module.scm";

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ------------------------------------------------------------------
    // 测试夹具：模拟一条"未定价"与一条"价格为零"的商品行
    // ------------------------------------------------------------------

    /**
     * 模拟 SCM 价格视图对象。
     */
    static class ScmPriceFixture {

        /**
         * SCM 约定：同时声明 {@code using} 与 {@code nullsUsing}。
         * Jackson 的 {@code using} 不会作用于 null，必须靠 {@code nullsUsing} 显式兜住 null，
         * 否则 null 的最终表现取决于全局 NullValueSerializer，容易在配置变动后失守。
         */
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        public BigDecimal unitPrice;

        public String priceStatus;

        ScmPriceFixture(BigDecimal unitPrice, String priceStatus) {
            this.unitPrice = unitPrice;
            this.priceStatus = priceStatus;
        }
    }

    // ------------------------------------------------------------------
    // 1. 序列化器契约
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SCM 序列化器：null 保持为 null，绝不写成 0")
    void nullStaysNull() throws Exception {
        String json = objectMapper.writeValueAsString(new ScmPriceFixture(null, "UNPRICED"));

        assertThat(json).contains("\"unitPrice\":null");
        assertThat(json).doesNotContain("\"unitPrice\":0");
        assertThat(json).doesNotContain("\"unitPrice\":\"0.0000\"");
    }

    @Test
    @DisplayName("SCM 序列化器：真正的 0 元输出 \"0.0000\"，与 null 严格区分")
    void zeroIsZeroString() throws Exception {
        String json = objectMapper.writeValueAsString(new ScmPriceFixture(BigDecimal.ZERO, "PRICED"));

        assertThat(json).contains("\"unitPrice\":\"0.0000\"");
    }

    @Test
    @DisplayName("SCM 序列化器：null 与 0 的序列化结果必须不同")
    void nullAndZeroAreDistinguishable() throws Exception {
        String unpriced = objectMapper.writeValueAsString(new ScmPriceFixture(null, "UNPRICED"));
        String zeroPriced = objectMapper.writeValueAsString(new ScmPriceFixture(BigDecimal.ZERO, "PRICED"));

        assertThat(unpriced).isNotEqualTo(zeroPriced);
    }

    @Test
    @DisplayName("SCM 序列化器：金额按 4 位小数 HALF_UP 输出为字符串")
    void scaleAndRounding() throws Exception {
        assertThat(objectMapper.writeValueAsString(new ScmPriceFixture(new BigDecimal("12.34565"), "PRICED")))
                .contains("\"unitPrice\":\"12.3457\"");
        assertThat(objectMapper.writeValueAsString(new ScmPriceFixture(new BigDecimal("12.34564"), "PRICED")))
                .contains("\"unitPrice\":\"12.3456\"");
        assertThat(objectMapper.writeValueAsString(new ScmPriceFixture(new BigDecimal("8"), "PRICED")))
                .contains("\"unitPrice\":\"8.0000\"");
    }

    // ------------------------------------------------------------------
    // 2. 对照：SmartAdmin 的序列化器在什么条件下才会把 null 变成 0
    // ------------------------------------------------------------------

    /**
     * 重要细节：Jackson 的 {@code @JsonSerialize(using = X)} <b>不会</b>作用于 null，
     * 只有 {@code nullsUsing = X} 才会。因此 {@code BigDecimalNullZeroSerializer} 的危险
     * 形式是 {@code nullsUsing}，而不是 {@code using}。
     */
    @Test
    @DisplayName("对照：using 不作用于 null，null 仍是 null")
    void usingDoesNotAffectNull() throws Exception {
        class UsingFixture {
            @JsonSerialize(using = BigDecimalNullZeroSerializer.class)
            public BigDecimal unitPrice;

            UsingFixture(BigDecimal unitPrice) {
                this.unitPrice = unitPrice;
            }
        }

        assertThat(objectMapper.writeValueAsString(new UsingFixture(null)))
                .isEqualTo("{\"unitPrice\":null}");
    }

    @Test
    @DisplayName("对照：nullsUsing 才会把 null 写成 0 —— 这正是 SCM 禁止的用法")
    void nullsUsingLosesNull() throws Exception {
        class NullsUsingFixture {
            @JsonSerialize(nullsUsing = BigDecimalNullZeroSerializer.class)
            public BigDecimal unitPrice;

            NullsUsingFixture(BigDecimal unitPrice) {
                this.unitPrice = unitPrice;
            }
        }

        String json = objectMapper.writeValueAsString(new NullsUsingFixture(null));

        // 语义丢失：UNPRICED 被伪装成 0 元
        assertThat(json).contains("\"unitPrice\":0");
        assertThat(json).doesNotContain("null");
    }

    // ------------------------------------------------------------------
    // 3. 防回归：SCM 包内不得出现 BigDecimalNullZeroSerializer
    // ------------------------------------------------------------------

    @Test
    @DisplayName("门禁：SCM 包内不得有任何类或字段使用 BigDecimalNullZeroSerializer")
    void scmPackageMustNotUseNullZeroSerializer() throws Exception {
        List<String> offenders = new ArrayList<>();

        ClassPathScanningCandidateComponentProvider provider =
                new ClassPathScanningCandidateComponentProvider(false);
        // 扫描全部候选组件（默认过滤器会排除接口与抽象类）
        provider.addIncludeFilter((metadataReader, metadataReaderFactory) -> true);

        Set<BeanDefinition> candidates = provider.findCandidateComponents(SCM_ROOT_PACKAGE);
        assertThat(candidates)
                .as("SCM 根包 %s 下应能扫描到类；若为空说明包名或扫描配置有误", SCM_ROOT_PACKAGE)
                .isNotEmpty();

        for (BeanDefinition definition : candidates) {
            Class<?> clazz = Class.forName(definition.getBeanClassName());

            if (hasNullZeroSerializer(clazz.getAnnotation(JsonSerialize.class))) {
                offenders.add(clazz.getName() + " (类级注解)");
            }
            for (Field field : clazz.getDeclaredFields()) {
                if (hasNullZeroSerializer(field.getAnnotation(JsonSerialize.class))) {
                    offenders.add(clazz.getName() + "#" + field.getName());
                }
            }
        }

        assertThat(offenders)
                .as("BigDecimalNullZeroSerializer 会把 null 变成 0，破坏 SCM 的 UNPRICED 语义。"
                        + "请改用 ScmFixedScale4Serializer。违规位置：%s", offenders)
                .isEmpty();
    }

    private static boolean hasNullZeroSerializer(JsonSerialize annotation) {
        if (annotation == null) {
            return false;
        }
        if (annotation.using() == BigDecimalNullZeroSerializer.class) {
            return true;
        }
        return annotation.nullsUsing() == BigDecimalNullZeroSerializer.class;
    }
}
