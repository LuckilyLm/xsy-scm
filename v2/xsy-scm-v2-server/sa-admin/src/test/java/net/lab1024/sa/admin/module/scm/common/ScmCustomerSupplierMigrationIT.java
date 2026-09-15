package net.lab1024.sa.admin.module.scm.common;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V8 / V9 migration 的落地验证（对应 Target Design §3）。
 *
 * <p>这个用例刻意**不**用 Spring 上下文：它要验证的是「migration 本身」能否在干净的应用之外
 * 独立跑通并留下正确的数据库结构，因此直接用 Flyway API + JDBC。
 *
 * <p>覆盖四类断言：
 * <ol>
 *   <li>V8 / V9 已成功应用，且 V6 / V7（W1）历史未被改动；</li>
 *   <li>4 张表与 5 条关键 CHECK 约束存在；</li>
 *   <li><b>R12 反陷阱</b>：{@code supplier_sku} 上不存在任何以 {@code is_default} 为键的唯一索引；</li>
 *   <li>菜单 / 权限点 / 角色授权 / 隐藏路由全部落库。</li>
 * </ol>
 */
class ScmCustomerSupplierMigrationIT {

    private static final String URL = "jdbc:postgresql://127.0.0.1:15432/xsy_scm?currentSchema=xsy_v2";

    private static String user() {
        return System.getenv().getOrDefault("XSY_V2_DB_USERNAME", "xsy_scm_app");
    }

    private static String password() {
        return System.getenv("XSY_V2_DB_PASSWORD");
    }

    private static long scalar(Statement statement, String sql) throws SQLException {
        try (ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    @Test
    void migrationsApplyValidateAndExposeTheW2Aggregate() throws Exception {
        Flyway flyway = Flyway.configure().dataSource(URL, user(), password())
                .schemas("xsy_v2").defaultSchema("xsy_v2").cleanDisabled(true)
                .placeholderReplacement(false).locations("classpath:db/migration").load();
        flyway.migrate();
        flyway.validate();

        try (Connection connection = DriverManager.getConnection(URL, user(), password());
             Statement statement = connection.createStatement()) {

            // ---- 1. 迁移历史：V8/V9 新增，V6/V7 未被触碰 ----
            assertThat(scalar(statement,
                    "SELECT count(*) FROM flyway_schema_history WHERE success AND version IN ('8','9')"))
                    .as("V8 / V9 必须各有一条成功记录").isEqualTo(2);
            assertThat(scalar(statement,
                    "SELECT count(*) FROM flyway_schema_history WHERE success AND version IN ('6','7')"))
                    .as("W1 的 V6 / V7 不得被回改或重跑").isEqualTo(2);

            // ---- 2. 表与约束 ----
            assertThat(scalar(statement,
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema='xsy_v2' "
                            + "AND table_name IN ('customer_type','customer','supplier','supplier_sku')"))
                    .as("W2 四张表").isEqualTo(4);
            assertThat(scalar(statement,
                    "SELECT count(*) FROM pg_constraint WHERE connamespace = 'xsy_v2'::regnamespace "
                            + "AND conname IN ("
                            + "'ck_customer_credit_period','ck_customer_self_parent','ck_customer_status',"
                            + "'ck_customer_settle_mode','ck_supplier_status','ck_supplier_sku_spec_values')"))
                    .as("关键 CHECK 约束").isEqualTo(6);

            // partial unique：活动编码唯一，软删后可复用
            assertThat(scalar(statement,
                    "SELECT count(*) FROM pg_indexes WHERE schemaname='xsy_v2' AND indexname IN ("
                            + "'uk_customer_code_active','uk_customer_type_code_active',"
                            + "'uk_supplier_code_active','uk_supplier_sku_active')"))
                    .as("四条 partial unique index").isEqualTo(4);

            // ---- 3. R12 反陷阱：绝不存在「每个供应商只能有一条默认」的唯一索引 ----
            assertThat(scalar(statement,
                    "SELECT count(*) FROM pg_indexes WHERE schemaname='xsy_v2' AND tablename='supplier_sku' "
                            + "AND indexdef ILIKE '%is_default%'"))
                    .as("R12：不得为 is_default 建任何唯一约束或索引").isZero();

            // ---- 4. 客户类型种子（legacy 的可维护字典，不是硬编码枚举）----
            assertThat(scalar(statement,
                    "SELECT count(*) FROM customer_type WHERE deleted=FALSE "
                            + "AND type_code IN ('ENTERPRISE','PERSONAL','GROUP')"))
                    .as("C 的 CustomerTypeEnum 落为种子数据").isEqualTo(3);

            // ---- 5. 菜单与权限点 ----
            assertThat(scalar(statement, "SELECT count(*) FROM t_menu WHERE menu_id IN (431,432,433,434,441,442,443,444,445,451,452,453,454,461,462,463,464,471,472,473,474,475,481,482)"))
                    .as("W2 菜单 / 权限点 24 条").isEqualTo(24);
            assertThat(scalar(statement,
                    "SELECT count(*) FROM t_menu WHERE menu_id IN (434,463) AND visible_flag = FALSE"))
                    .as("客户详情 / 供应商详情是隐藏但可深链的路由").isEqualTo(2);
            assertThat(scalar(statement,
                    "SELECT count(*) FROM t_menu WHERE (component LIKE '/business/scm/customer/%' "
                            + "OR component LIKE '/business/scm/supplier/%') AND menu_id IN (431,432,433,434,441,442,443,444,445,451,452,453,454,461,462,463,464,471,472,473,474,475,481,482)"))
                    .as("组件路径必须与 src/views/** 一一对应").isEqualTo(6);
            assertThat(scalar(statement,
                    "SELECT count(*) FROM t_menu WHERE (api_perms LIKE 'scm:customer:%' "
                            + "OR api_perms LIKE 'scm:supplier:%') AND menu_id IN (431,432,433,434,441,442,443,444,445,451,452,453,454,461,462,463,464,471,472,473,474,475,481,482)"))
                    .as("SCM 权限码").isEqualTo(16);
            assertThat(scalar(statement,
                    "SELECT count(*) FROM t_role_menu WHERE role_id = 1 AND menu_id IN (431,432,433,434,441,442,443,444,445,451,452,453,454,461,462,463,464,471,472,473,474,475,481,482)"))
                    .as("管理员角色授权").isEqualTo(24);

            // 菜单 id 序列已校正，后续迁移不会撞主键
            assertThat(scalar(statement,
                    "SELECT last_value FROM t_menu_menu_id_seq"))
                    .as("setval 已把 menu_id 序列推到 max+1 之上").isGreaterThanOrEqualTo(483L);
        }
    }
}
