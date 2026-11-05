package com.xsy.scm.admin.test;

import java.net.URI;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.springframework.test.context.DynamicPropertyRegistry;

/** 保存模块基线的独立测试库；历史DDL断言不连接执行后续迁移的业务回归库。 */
public final class PgITBaselineDatabase {
    private static String baselineUrl;

    private PgITBaselineDatabase() {
    }

    /** @return 隔离的模块基线URL；要求调用者显式提供以_it结尾的测试库，否则拒绝建库。 */
    public static synchronized String url() {
        if (baselineUrl != null) {
            return baselineUrl;
        }
        String primary = PgITDatabase.url();
        String database = URI.create(primary.substring("jdbc:".length())).getPath().substring(1);
        if (!database.matches("[a-zA-Z0-9_]+_it")) {
            throw new IllegalStateException("基线DDL验证必须显式指定独立_it测试库");
        }
        String name = database.substring(0, database.length() - 3) + "_baseline_it";
        if (name.length() > 63) {
            throw new IllegalStateException("隔离基线库名称过长");
        }
        try (var connection = DriverManager.getConnection(primary, PgITDatabase.user(), PgITDatabase.password())) {
            boolean exists;
            try (var query = connection.prepareStatement("SELECT 1 FROM pg_database WHERE datname=?")) {
                query.setString(1, name);
                try (var result = query.executeQuery()) {
                    exists = result.next();
                }
            }
            if (!exists) {
                try (var statement = connection.createStatement()) {
                    statement.execute("CREATE DATABASE \"" + name + "\" TEMPLATE template0");
                }
            }
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("无法准备隔离基线库", exception);
        }
        String target = primary.replace("/" + database, "/" + name);
        Flyway flyway = Flyway.configure().dataSource(target, PgITDatabase.user(), PgITDatabase.password())
            .schemas("xsy_v2").defaultSchema("xsy_v2").cleanDisabled(true).placeholderReplacement(false)
            .locations("classpath:db/migration/modules").target("1000.24").load();
        flyway.migrate();
        flyway.validate();
        baselineUrl = target;
        return target;
    }

    /** @param registry 非空Spring测试属性注册器；只切换历史DDL用例的数据源和迁移目标。 */
    public static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PgITBaselineDatabase::url);
        registry.add("spring.flyway.target", () -> "1000.24");
    }
}
