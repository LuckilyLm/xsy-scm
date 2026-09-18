package net.lab1024.sa.admin.module.system;

import net.lab1024.sa.admin.test.PgITDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V12 的落地验证：D-1（{@code t_oa_enterprise_employee} 关联列类型不匹配）按方案 A 在 schema 根因修复。
 *
 * <p>与 {@code ScmCustomerSupplierMigrationIT} 一样刻意**不**启动 Spring 上下文：这里验证的是
 * 「migration 本身」能否独立跑通并留下正确结构。
 *
 * <p>覆盖四类断言：
 * <ol>
 *   <li>V12 已成功应用，且 V1–V11 历史未被改动（11 条全部 success）；</li>
 *   <li><b>核心断言</b>：{@code enterprise_id} / {@code employee_id} 两列的 PG 类型确实是
 *       {@code bigint}（{@code information_schema.columns.data_type='bigint'} 且
 *       {@code udt_name='int8'}），不是 {@code character varying}；</li>
 *   <li>列类型变更后，唯一键与两个索引被 PG 自动重建且仍然有效；</li>
 *   <li><b>行为断言</b>：原先报 {@code operator does not exist: character varying = bigint}
 *       的两条真实语句（JOIN 查询 + Long 参数比较）现在能执行。</li>
 * </ol>
 */
class SmartAdminOaEnterpriseEmployeeMigrationIT {

    private static final String URL = PgITDatabase.url();

    private static String user() {
        return PgITDatabase.user();
    }

    private static String password() {
        return PgITDatabase.password();
    }

    private static long scalar(Statement statement, String sql) throws SQLException {
        try (ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static String text(Statement statement, String sql) throws SQLException {
        try (ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }

    @Test
    void v12ConvertsOaEnterpriseEmployeeJoinColumnsToBigint() throws Exception {
        Flyway flyway = Flyway.configure().dataSource(URL, user(), password())
                .schemas("xsy_v2").defaultSchema("xsy_v2").cleanDisabled(true)
                .placeholderReplacement(false).locations("classpath:db/migration").load();
        flyway.migrate();
        flyway.validate();

        try (Connection connection = DriverManager.getConnection(URL, user(), password());
             Statement statement = connection.createStatement()) {

            // ---- 1. 迁移历史：V12 新增且成功；V1–V11 全部保持 success ----
            assertThat(scalar(statement,
                    "SELECT count(*) FROM flyway_schema_history WHERE version = '12' AND success"))
                    .as("V12 必须有一条成功记录").isEqualTo(1);
            assertThat(scalar(statement,
                    "SELECT count(*) FROM flyway_schema_history WHERE success AND version IN "
                            + "('1','2','3','4','5','6','7','8','9','10','11')"))
                    .as("V1–V11 不得被回改或重跑").isEqualTo(11);
            assertThat(scalar(statement,
                    "SELECT count(*) FROM flyway_schema_history WHERE version = '12' AND checksum IS NULL"))
                    .as("V12 的 checksum 必须已登记").isZero();

            // ---- 2. 核心断言：两列必须是 bigint ----
            for (String column : new String[]{"enterprise_id", "employee_id"}) {
                assertThat(text(statement,
                        "SELECT data_type FROM information_schema.columns WHERE table_schema='xsy_v2' "
                                + "AND table_name='t_oa_enterprise_employee' AND column_name='" + column + "'"))
                        .as("t_oa_enterprise_employee.%s 的 data_type 必须是 bigint", column)
                        .isEqualTo("bigint");
                assertThat(text(statement,
                        "SELECT udt_name FROM information_schema.columns WHERE table_schema='xsy_v2' "
                                + "AND table_name='t_oa_enterprise_employee' AND column_name='" + column + "'"))
                        .as("t_oa_enterprise_employee.%s 的 udt_name 必须是 int8", column)
                        .isEqualTo("int8");
                assertThat(scalar(statement,
                        "SELECT count(*) FROM information_schema.columns WHERE table_schema='xsy_v2' "
                                + "AND table_name='t_oa_enterprise_employee' AND column_name='" + column + "' "
                                + "AND data_type LIKE 'character%'"))
                        .as("t_oa_enterprise_employee.%s 不得再是字符类型", column)
                        .isZero();
                assertThat(scalar(statement,
                        "SELECT count(*) FROM information_schema.columns "
                                + "WHERE table_schema='xsy_v2' AND table_name='t_oa_enterprise_employee' "
                                + "AND column_name='" + column + "' AND character_maximum_length IS NOT NULL"))
                        .as("bigint 列不得再带长度语义").isZero();
            }

            // ---- 3. 唯一键与索引随列类型重建，且仍然存在 ----
            assertThat(scalar(statement,
                    "SELECT count(*) FROM pg_constraint WHERE connamespace='xsy_v2'::regnamespace "
                            + "AND conname='uk_t_oa_enterprise_employee_uk_enterprise_employee' AND contype='u'"))
                    .as("(enterprise_id, employee_id) 唯一键必须保留").isEqualTo(1);
            assertThat(scalar(statement,
                    "SELECT count(*) FROM pg_indexes WHERE schemaname='xsy_v2' AND tablename='t_oa_enterprise_employee' "
                            + "AND indexname IN ('idx_t_oa_enterprise_employee_idx_employee_id',"
                            + "'idx_t_oa_enterprise_employee_idx_enterprise_id')"))
                    .as("两个索引必须保留").isEqualTo(2);
            // 唯一键的列类型也必须已跟随变为 int8（而不是重建成了 text）
            assertThat(scalar(statement,
                    "SELECT count(*) FROM pg_index i JOIN pg_attribute a "
                            + "  ON a.attrelid = i.indexrelid AND a.attnum > 0 "
                            + "WHERE i.indexrelid = 'xsy_v2.uk_t_oa_enterprise_employee_uk_enterprise_employee'::regclass "
                            + "  AND a.atttypid = 'int8'::regtype"))
                    .as("唯一键的两个键列都必须已是 int8").isEqualTo(2);

            // ---- 4. 行为断言：原先失败的语句现在可执行 ----
            // 4a. JOIN 比较（D-1 报告里的原始失败语句形态）
            scalar(statement,
                    "SELECT count(*) FROM t_oa_enterprise_employee e "
                            + "LEFT JOIN t_oa_enterprise en ON e.enterprise_id = en.enterprise_id "
                            + "LEFT JOIN t_employee emp ON e.employee_id = emp.employee_id");
            // 4b. JDBC 强类型参数（Long → int8），D-1 里 PREPARE 失败的形态
            try (var prepared = connection.prepareStatement(
                    "SELECT count(*) FROM t_oa_enterprise_employee WHERE enterprise_id = ? "
                            + "AND employee_id IN (?)")) {
                prepared.setLong(1, 1L);
                prepared.setLong(2, 1L);
                prepared.executeQuery();
            }
            // 4c. 写路径：Long → bigint 赋值（PG 允许数值→text，反向则需显式转换，故这是必须验证的方向）
            try (var prepared = connection.prepareStatement(
                    "INSERT INTO t_oa_enterprise_employee(enterprise_id, employee_id) VALUES (?, ?)")) {
                prepared.setLong(1, -1L);
                prepared.setLong(2, -1L);
                prepared.executeUpdate();
            }
            assertThat(scalar(statement,
                    "SELECT count(*) FROM t_oa_enterprise_employee WHERE enterprise_id = -1 AND employee_id = -1"))
                    .as("写入的探测行应可读回（读写类型一致）").isEqualTo(1);
            statement.execute("DELETE FROM t_oa_enterprise_employee WHERE enterprise_id = -1 AND employee_id = -1");
            assertThat(scalar(statement, "SELECT count(*) FROM t_oa_enterprise_employee"))
                    .as("清理后探测行不得残留").isZero();
        }
    }
}
