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
        String expected = System.getProperty("xsy.migration.expected-version", "128");
        String current = jdbc.queryForObject("SELECT version FROM flyway_schema_history "
                + "WHERE version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1", String.class);
        assertThat(current).isEqualTo(expected);
    }

    /**
     * V128 新增列必须落库：券模板领取三列、订单预选券列、券实例发放来源列。
     * 只在目标版本 >= 128 时校验，避免在 V127 及更早的演练中误报。
     */
    @Test
    void mallClaimColumnsExistWhenTargetIs128OrLater() {
        int target = Integer.parseInt(System.getProperty("xsy.migration.expected-version", "128"));
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
     * V2 起不引入数据库外键（见 smartadmin-foundation.md）。V127 移除了全库最后一个外键，
     * 之后必须保持 0。这里统计 xsy_v2 schema 下所有外键约束数量。
     */
    @Test
    void noForeignKeysAcrossSchema() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint c "
                        + "WHERE c.contype = 'f' AND c.connamespace = 'xsy_v2'::regnamespace",
                Integer.class);
        assertThat(count).isZero();
    }

    private void assertColumn(String table, String column) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'xsy_v2' AND table_name = ? AND column_name = ?",
                Integer.class, table, column);
        assertThat(count).as("%s.%s should exist", table, column).isEqualTo(1);
    }
}
