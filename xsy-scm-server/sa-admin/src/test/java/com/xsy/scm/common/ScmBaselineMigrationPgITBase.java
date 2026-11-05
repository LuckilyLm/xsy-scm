package com.xsy.scm.common;

import net.lab1024.sa.admin.test.PgITBaselineDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 仅供保存V1000模块DDL验证；当前业务与V1001结构使用普通PgIT基类。 */
public abstract class ScmBaselineMigrationPgITBase extends ScmW6PgITBase {
    /** @param registry 非空测试属性注册器；使用独立基线库，避免污染当前业务回归库。 */
    @DynamicPropertySource
    static void baselineProperties(DynamicPropertyRegistry registry) {
        PgITBaselineDatabase.register(registry);
    }
}
