package net.lab1024.sa.admin.config;

import net.lab1024.sa.admin.module.scm.common.json.ScmOffsetDateTimeDeserializer;
import net.lab1024.sa.admin.module.scm.common.json.ScmOffsetDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.OffsetDateTime;

/**
 * SCM 域 JSON 序列化补充配置。
 *
 * <p><b>背景：</b>SmartAdmin 的 {@code net.lab1024.sa.base.config.JsonConfig} 只注册了
 * {@link java.time.LocalDate} 与 {@link java.time.LocalDateTime} 的序列化器，
 * 没有覆盖 {@link OffsetDateTime}。而 SCM 域的 VO 统一使用 {@code OffsetDateTime}
 * （138 个字段，SCM 之外无使用），导致这些字段走 Jackson 默认 ISO-8601 输出：
 * <pre>
 *   2026-09-17T13:50:57.394795+08:00
 * </pre>
 * 前端直接渲染后表现为「日期时间粘连 + 6 位微秒 + 时区后缀」，
 * 与 {@code sa-base.yaml} 中 {@code date-format: yyyy-MM-dd HH:mm:ss} 的意图不符。
 *
 * <p><b>为什么单独建一个类、而不改 sa-base 的 JsonConfig：</b>
 * {@code sa-base} 属 SmartAdmin 底座，按项目规则不承载 SCM 业务定制；
 * 且 {@code OffsetDateTime} 仅 SCM 使用，把定制收敛在 sa-admin 侧
 * 可避免影响底座页面，也便于后续单独回滚。
 *
 * @see net.lab1024.sa.base.config.JsonConfig
 * @see ScmOffsetDateTimeSerializer
 */
@Configuration
public class ScmJsonConfig {

    /**
     * 注册 SCM 的 {@link OffsetDateTime} 序列化器与反序列化器。
     *
     * <p>Spring Boot 会把容器里所有 {@link Jackson2ObjectMapperBuilderCustomizer}
     * 依次应用到同一个 {@code Jackson2ObjectMapperBuilder} 上，
     * 因此这里注册的 {@code serializerByType} 与底座 {@code JsonConfig} 的注册互不冲突，
     * 只补上底座遗漏的那一种类型。
     *
     * <p><b>反序列化器是必需的对称件</b>（2026-09-18 补齐）：只注册序列化器会让
     * 「API 返回什么就能送回来什么」这条往返契约断掉，而幂等重放正是靠它把库里的 JSON
     * 还原成 VO —— 缺了它，所有含 {@code OffsetDateTime} 的写命令在重放时都会抛异常。
     * 详见 {@link ScmOffsetDateTimeDeserializer} 的类注释。
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer scmOffsetDateTimeCustomizer() {
        return builder -> builder
                .serializerByType(OffsetDateTime.class, new ScmOffsetDateTimeSerializer())
                .deserializerByType(OffsetDateTime.class, new ScmOffsetDateTimeDeserializer());
    }
}
