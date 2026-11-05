package com.xsy.scm.common;

import com.xsy.scm.customer.permission.CustomerPermission;
import com.xsy.scm.supplier.permission.SupplierPermission;
import net.lab1024.sa.admin.test.PgITDatabase;
import net.lab1024.sa.admin.test.PgITBaselineDatabase;
import net.lab1024.sa.admin.test.PgITMigrationHistory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V8 / V9 两个迁移的落地验证。
 *
 * <p>本用例不加载 Spring 上下文：要验证的是迁移脚本本身能否在应用之外独立跑通
 * 并留下正确的数据库结构，因此直接用 Flyway API + JDBC。
 *
 * <p>覆盖四类断言：
 * <ol>
 *   <li>V6～V9由成功的增量或B保存的数据覆盖，Flyway校验通过；</li>
 *   <li>4 张表与 5 条关键 CHECK 约束存在；</li>
 *   <li><b>反陷阱</b>：{@code xsy_supplier_sku} 上不存在任何以 {@code is_default} 为键的唯一索引；</li>
 *   <li>菜单 / 权限点 / 角色授权 / 隐藏路由全部写入数据库。</li>
 * </ol>
 */
class ScmCustomerSupplierMigrationIT {

    private static final String URL = PgITBaselineDatabase.url();

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

    private static List<String> values(Statement statement, String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                values.add(result.getString(1));
            }
        }
        return values;
    }

    @Test
    void migrationsApplyValidateAndExposeTheW2Aggregate() throws Exception {
        Flyway flyway = Flyway.configure().dataSource(URL, user(), password())
                .schemas("xsy_v2").defaultSchema("xsy_v2").cleanDisabled(true)
                .placeholderReplacement(false)
                .locations("classpath:db/migration/modules").target("1000.24").load();
        flyway.migrate();
        flyway.validate();

        try (Connection connection = DriverManager.getConnection(URL, user(), password());
             Statement statement = connection.createStatement()) {

            PgITMigrationHistory.assertCovered(statement, "6", "7", "8", "9");

            // ---- 2. 表与约束 ----
            assertThat(scalar(statement,
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema='xsy_v2' "
                            + "AND table_name IN ('xsy_customer','xsy_supplier','xsy_supplier_sku')"))
                    .as("W2 三张表（客户类型表已由 V231 下线）").isEqualTo(3);
            assertThat(scalar(statement,
                    "SELECT count(*) FROM pg_constraint WHERE connamespace = 'xsy_v2'::regnamespace "
                            + "AND conname IN ("
                            + "'ck_customer_credit_period','ck_customer_self_parent','ck_customer_status',"
                            + "'ck_customer_settle_mode','ck_supplier_status','ck_supplier_sku_spec_values')"))
                    .as("关键 CHECK 约束").isEqualTo(6);

            // partial unique：活动编码唯一，软删后可复用
            assertThat(scalar(statement,
                    "SELECT count(*) FROM pg_indexes WHERE schemaname='xsy_v2' AND indexname IN ("
                            + "'uk_customer_code_active','uk_supplier_code_active','uk_supplier_sku_active')"))
                    .as("三条 partial unique index").isEqualTo(3);

            // ---- 3. 反陷阱：绝不存在「每个供应商只能有一条默认」的唯一索引 ----
            assertThat(scalar(statement,
                    "SELECT count(*) FROM pg_indexes WHERE schemaname='xsy_v2' AND tablename='xsy_supplier_sku' "
                            + "AND indexdef ILIKE '%is_default%'"))
                    .as("R12：不得为 is_default 建任何唯一约束或索引").isZero();

            // ---- 4. V231：客户类型相关结构已下线 ----
            assertThat(scalar(statement,
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema='xsy_v2' "
                            + "AND table_name IN ('xsy_customer_type','xsy_customer_type_price',"
                            + "'xsy_customer_price_batch_audit')"))
                    .as("客户类型 / 类型价 / 类型价批量审计表都必须被删除").isZero();
            assertThat(scalar(statement,
                    "SELECT count(*) FROM information_schema.columns WHERE table_schema='xsy_v2' "
                            + "AND table_name='xsy_customer' AND column_name='customer_type_id'"))
                    .as("客户表不得再保留 customer_type_id").isZero();

            // ---- 5. 菜单与权限点 ----
            String activeIds = "431,432,434,441,442,443,444,445,461,462,463,464,471,472,473,474,475,481,482";
            // 当前初始化不保存已下线类型菜单的软删占位；已有库仍可保留这些历史行。
            assertThat(values(statement, "SELECT menu_id::text FROM t_menu WHERE NOT deleted_flag AND menu_id IN ("
                    + activeIds + ") ORDER BY menu_id"))
                    .as("现行客户/供应商菜单和权限必须逐项齐全")
                    .containsExactly("431", "432", "434", "441", "442", "443", "444", "445", "461", "462",
                            "463", "464", "471", "472", "473", "474", "475", "481", "482");
            assertThat(scalar(statement,
                    "SELECT count(*) FROM t_menu WHERE menu_id IN (433,451,452,453,454) AND NOT deleted_flag"))
                    .as("已下线客户类型菜单与权限不得重新启用").isZero();
            assertThat(scalar(statement,
                    "SELECT count(*) FROM t_menu WHERE menu_id IN (434,463) AND visible_flag = FALSE AND NOT deleted_flag"))
                    .as("客户详情 / 供应商详情是隐藏但可深链的路由").isEqualTo(2);
            assertThat(values(statement, "SELECT component FROM t_menu WHERE NOT deleted_flag AND component IS NOT NULL "
                    + "AND menu_id IN (" + activeIds + ")"))
                    .as("当前页面组件路径必须逐项对应正式页面")
                    .containsExactlyInAnyOrder("/business/scm/customer/customer-list.vue",
                            "/business/scm/customer/customer-detail.vue", "/business/scm/supplier/supplier-list.vue",
                            "/business/scm/supplier/supplier-detail.vue", "/business/scm/supplier/supplier-sku-list.vue");
            assertThat(values(statement, "SELECT api_perms FROM t_menu WHERE NOT deleted_flag AND api_perms IS NOT NULL "
                    + "AND menu_id IN (" + activeIds + ")"))
                    .as("正式CRUD与供应商品目权限不得遗漏或写错")
                    .containsExactlyInAnyOrder(CustomerPermission.QUERY, CustomerPermission.ADD, CustomerPermission.UPDATE,
                            CustomerPermission.STATUS, CustomerPermission.DELETE, SupplierPermission.QUERY,
                            SupplierPermission.ADD, SupplierPermission.UPDATE, SupplierPermission.STATUS,
                            SupplierPermission.DELETE, SupplierPermission.SKU_QUERY, SupplierPermission.SKU_UPDATE);
            assertThat(scalar(statement,
                    "SELECT count(*) FROM t_role_menu WHERE role_id = 1 AND menu_id IN (431,432,433,434,441,442,443,444,445,451,452,453,454,461,462,463,464,471,472,473,474,475,481,482)"))
                    .as("管理员角色授权（客户类型菜单 433/451-454 已由 V231 撤回）").isEqualTo(19);
            assertThat(scalar(statement,
                    "SELECT count(*) FROM t_role_menu WHERE menu_id IN (433,451,452,453,454)"))
                    .as("任何角色都不得残留已撤回类型权限").isZero();

            // 菜单 id 序列已校正，后续迁移不会撞主键
            assertThat(scalar(statement,
                    "SELECT last_value FROM t_menu_menu_id_seq"))
                    .as("setval 已把 menu_id 序列推到 max+1 之上").isGreaterThanOrEqualTo(483L);
        }
    }
}
