package net.lab1024.sa.admin.config;

import com.xsy.scm.common.json.ScmOffsetDateTimeDeserializer;
import com.xsy.scm.common.json.ScmOffsetDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.OffsetDateTime;

/**
 * 为 SCM 的 OffsetDateTime 注册统一日期格式（yyyy-MM-dd HH:mm:ss）。
 * 这项配置独立于 SmartAdmin 通用 JSON 设置，避免影响其他业务。
 */
@Configuration
public class ScmJsonConfig {

    /** 补充 SCM 时间与金额的 JSON 序列化规则，保证前后端类型和时区口径一致。 */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer scmOffsetDateTimeCustomizer() {
        return builder -> builder
                .serializerByType(OffsetDateTime.class, new ScmOffsetDateTimeSerializer())
                .deserializerByType(OffsetDateTime.class, new ScmOffsetDateTimeDeserializer());
    }
}
