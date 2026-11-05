package com.xsy.scm.common;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.Map;
import com.xsy.scm.admin.test.PgITDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.assertThat;

/** 对显式准备的现有库副本执行真实升级，验证全部业务行和序列不变。 */
@EnabledIfEnvironmentVariable(named = "XSY_RULES_UPGRADE_DB_URL", matches = ".+")
class ScmBackendRulesUpgradePgIT {
    @Test
    void upgradePreservesEveryBusinessRowAndSequence() throws Exception {
        String url = System.getenv("XSY_RULES_UPGRADE_DB_URL");
        // 只能连接专用副本，不允许以正式库或普通业务回归库执行该迁移演练。
        assertThat(url).matches("jdbc:postgresql://[^/]+/[a-zA-Z0-9_]+_upgrade_it(?:\\?.*)?");
        Flyway flyway = Flyway.configure().dataSource(url, PgITDatabase.user(), PgITDatabase.password())
            .schemas("xsy_v2").defaultSchema("xsy_v2").cleanDisabled(true).placeholderReplacement(false)
            .locations("classpath:db/migration/modules").load();
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("1000.24");
        try (var connection = DriverManager.getConnection(url, PgITDatabase.user(), PgITDatabase.password())) {
            Map<String, String> before = snapshot(connection);
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            flyway.validate();
            assertThat(flyway.info().current().getVersion().toString()).isEqualTo("1001");
            assertThat(snapshot(connection)).isEqualTo(before);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            assertThat(snapshot(connection)).isEqualTo(before);
        }
    }

    private Map<String, String> snapshot(Connection connection) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        try (var query = connection.createStatement(); var rows = query.executeQuery(
            "SELECT tablename FROM pg_tables WHERE schemaname='xsy_v2' "
                + "AND tablename <> 'flyway_schema_history' ORDER BY tablename")) {
            while (rows.next()) {
                String table = rows.getString(1);
                try (var statement = connection.createStatement(); var digest = statement.executeQuery(
                    "SELECT count(*) || ':' || coalesce(md5(string_agg(d, '' ORDER BY d)), '') FROM "
                        + "(SELECT md5(to_jsonb(t)::text) d FROM xsy_v2.\"" + table + "\" t) s")) {
                    digest.next();
                    result.put("table:" + table, digest.getString(1));
                }
            }
        }
        try (var query = connection.createStatement(); var rows = query.executeQuery(
            "SELECT sequencename FROM pg_sequences WHERE schemaname='xsy_v2' ORDER BY sequencename")) {
            while (rows.next()) {
                String sequence = rows.getString(1);
                try (var statement = connection.createStatement(); var state = statement.executeQuery(
                    "SELECT last_value || ':' || is_called FROM xsy_v2.\"" + sequence + "\"")) {
                    state.next();
                    result.put("sequence:" + sequence, state.getString(1));
                }
            }
        }
        return result;
    }
}
