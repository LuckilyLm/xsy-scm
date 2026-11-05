package com.xsy.scm.base.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import static org.assertj.core.api.Assertions.assertThat;

/** 配置文件改名后仍须被加载，不能只编译成功却缺少数据库等基础配置。 */
class YamlProcessorTest {
    @Test
    void renamedSharedConfigurationIsLoaded() {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlProcessor().postProcessEnvironment(environment, null);
        assertThat(environment.containsProperty("spring.datasource.url")).isTrue();
        assertThat(environment.getProperty("spring.flyway.locations")).isEqualTo("classpath:db/migration/modules");
        assertThat(environment.containsProperty("xsy.job.enabled")).isTrue();
    }
}
