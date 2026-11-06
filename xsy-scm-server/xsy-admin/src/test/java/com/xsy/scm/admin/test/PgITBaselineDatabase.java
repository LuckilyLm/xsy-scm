package com.xsy.scm.admin.test;

import cn.hutool.crypto.symmetric.SM4;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.test.context.DynamicPropertyRegistry;
import static org.assertj.core.api.Assertions.assertThat;


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
            .schemas("xsy").defaultSchema("xsy").cleanDisabled(true).placeholderReplacement(false)
            .locations("classpath:db/migration/modules").target("1000.24").load();
        flyway.migrate();
        flyway.validate();
        baselineUrl = target;
        return target;
    }

    /** @param registry 非空Spring测试属性注册器；只切换历史DDL用例的数据源和迁移目标。 */
    public static void register(DynamicPropertyRegistry registry) {
        // Spring 数据源配置使用 P6Spy 驱动，URL 必须保留 jdbc:p6spy: 前缀；
        // Flyway 与裸 JDBC 连接则使用剥掉前缀的 url()。
        registry.add("spring.datasource.url", () -> "jdbc:p6spy:" + url().substring("jdbc:".length()));
        registry.add("spring.flyway.target", () -> "1000.24");
    }
}
