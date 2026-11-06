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


public final class PgITMigrationHistory {
    private PgITMigrationHistory() {
    }

    /**
     * @param statement 已连接隔离测试库的非空查询入口，不在此关闭
     * @param versions 必须覆盖的原版本或模块版本号，不允许为空
     * @throws SQLException 历史查询失败；不将缺历史视为已经安装
     */
    public static void assertCovered(Statement statement, String... versions) throws SQLException {
        Set<MigrationVersion> applied = new HashSet<>();
        boolean cutover = false;
        try (var result = statement.executeQuery(
                "SELECT version,type,description,script,checksum FROM flyway_schema_history WHERE success AND version IS NOT NULL")) {
            while (result.next()) {
                MigrationVersion version = MigrationVersion.fromVersion(result.getString("version"));
                if ("BASELINE".equals(result.getString("type")) && "1000.24".equals(version.toString())
                        && "SCM module baseline V244".equals(result.getString("description"))) {
                    cutover = true;
                } else if ("SQL".equals(result.getString("type")) && result.getObject("checksum") != null
                        && result.getString("script").matches("V[0-9.]+__.*\\.sql")) {
                    applied.add(version);
                }
            }
        }
        if (cutover) {
            try (var result = statement.executeQuery(
                    "SELECT to_regclass('xsy.flyway_schema_history_legacy') IS NOT NULL")) {
                result.next();
                assertThat(result.getBoolean(1)).as("切换基线必须保留原历史表").isTrue();
            }
        }
        boolean modulesComplete = cutover || applied.contains(MigrationVersion.fromVersion("1000.24"));
        for (String version : versions) {
            MigrationVersion required = MigrationVersion.fromVersion(version);
            boolean coveredLegacy = modulesComplete && required.compareTo(MigrationVersion.fromVersion("244")) <= 0;
            assertThat(applied.contains(required) || coveredLegacy)
                    .as("版本%s须有成功记录或被完整模块基线覆盖", version).isTrue();
        }
    }
}
