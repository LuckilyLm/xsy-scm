package com.xsy.scm.common;

import net.lab1024.sa.admin.AdminApplication;
import net.lab1024.sa.admin.test.PgITPaths;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AdminApplication.class, properties = {
        "project.log-directory=" + PgITPaths.DEFAULT_LOG_DIR,
        "file.storage.local.upload-path=" + PgITPaths.DEFAULT_UPLOAD_PATH,
        "file.storage.local.url-prefix=http://127.0.0.1:18082",
        "logging.level.root=WARN"})
class ScmMigrationTargetPgIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void reachesTheRequestedMigrationVersion() {
        String expected = System.getProperty("xsy.migration.expected-version", "133");
        String current = jdbc.queryForObject("SELECT version FROM flyway_schema_history "
                + "WHERE version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1", String.class);
        assertThat(current).isEqualTo(expected);
    }

    /**
     * 数据库版本达到 V128 时，必须包含券模板领取限制、订单预选券和券发放来源字段。
     * 目标版本早于 V128 时跳过检查。
     */
    @Test
    void mallClaimColumnsExistWhenTargetIs128OrLater() {
        int target = Integer.parseInt(System.getProperty("xsy.migration.expected-version", "133"));
        if (target < 128) {
            return;
        }
        assertColumn("xsy_promotion_coupon", "claimable");
        assertColumn("xsy_promotion_coupon", "per_customer_limit");
        assertColumn("xsy_promotion_coupon", "total_limit");
        assertColumn("xsy_sales_order", "mall_coupon_instance_id");
        assertColumn("xsy_promotion_coupon_instance", "issue_source");
    }

    /**
     * 数据库版本达到 V129 时，订单必须包含结算方式列和取值约束。
     * 结算方式仅表示约定，余额、授信和收款仍由各自业务模块管理。
     */
    @Test
    void settlementMethodColumnExistsWhenTargetIs129OrLater() {
        int target = Integer.parseInt(System.getProperty("xsy.migration.expected-version", "133"));
        if (target < 129) {
            return;
        }
        assertColumn("xsy_sales_order", "settlement_method");
        Integer allowed = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conname = 'ck_sales_order_settlement_method'",
                Integer.class);
        assertThat(allowed).as("结算方式取值约束应随列一起建立").isEqualTo(1);
    }

    /**
     * V2 数据库禁止外键约束（见 AGENTS.md）。V127 清理最后一个外键后，
     * xsy_v2 schema 的外键数量必须保持为零。
     */
    @Test
    void noForeignKeysAcrossSchema() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint c "
                        + "WHERE c.contype = 'f' AND c.connamespace = 'xsy_v2'::regnamespace",
                Integer.class);
        assertThat(count).isZero();
    }

    @Test
    void codPermissionDefaultsToDisabledFromV130() {
        int target = Integer.parseInt(System.getProperty("xsy.migration.expected-version", "133"));
        if (target < 130) {
            return;
        }
        assertColumn("xsy_customer", "cod_allowed");
        Integer notAllowed = jdbc.queryForObject(
                "SELECT count(*) FROM xsy_customer WHERE cod_allowed IS NULL", Integer.class);
        assertThat(notAllowed).isZero();
        String defaultExpression = jdbc.queryForObject(
                "SELECT column_default FROM information_schema.columns "
                        + "WHERE table_schema = 'xsy_v2' AND table_name = 'xsy_customer' AND column_name = 'cod_allowed'",
                String.class);
        assertThat(defaultExpression).contains("false");
    }

    @Test
    void codInstallmentOrderColumnExistsFromV132() {
        int target = Integer.parseInt(System.getProperty("xsy.migration.expected-version", "133"));
        if (target < 132) {
            return;
        }
        assertColumn("xsy_finance_receipt", "cod_order_id");
    }

    private void assertColumn(String table, String column) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'xsy_v2' AND table_name = ? AND column_name = ?",
                Integer.class, table, column);
        assertThat(count).as("%s.%s should exist", table, column).isEqualTo(1);
    }
}
