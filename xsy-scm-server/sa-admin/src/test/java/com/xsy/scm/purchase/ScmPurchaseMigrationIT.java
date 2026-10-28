package com.xsy.scm.purchase;

import com.xsy.scm.common.ScmW5PgITBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 采购域迁移的 schema 形状验收。
 *
 * <p>本类是唯一直接断言 schema 形状的地方（表 / 索引 / 序列 / 种子 / 外键 / 库存表 / CHECK），
 * 其余 IT 只断言行为。schema 一旦后续被悄悄改动，这里会先失败。
 *
 * <p><b>V1–V12 永久冻结、V15/V16 只追加</b>：本类不修改任何数据，只读元数据 + 断言约束生效。
 */
@DisplayName("W5 采购域迁移（PG IT）")
class ScmPurchaseMigrationIT extends ScmW5PgITBase {

    /**
     * V15 建出的 9 张采购域表。
     */
    private static final List<String> V15_TABLES = List.of(
            "xsy_warehouse",
            "xsy_purchase_demand",
            "xsy_purchase_demand_allocation",
            "xsy_purchase_order",
            "xsy_purchase_order_item",
            "xsy_purchase_receipt",
            "xsy_purchase_receipt_item",
            "xsy_receipt_weighing_record",
            "xsy_purchase_operation_log");

    /**
     * 逗号分隔的表名清单，配合 {@code string_to_array(?, ',')} 使用。
     *
     * <p>不直接传 {@code String[]} 的原因：{@code JdbcTemplate.queryForObject(sql, Class, Object...)}
     * 会把数组当成可变参数展开（9 个表名 → 9 个参数），而 SQL 里只有 1 个 {@code ?}，
     * 于是报「column index out of range」。拼成字符串再在 SQL 里切开，行为与驱动无关。
     */
    private static final String V15_TABLE_LIST = String.join(",", V15_TABLES);

    @Test
    @DisplayName("V15 建 9 表 + 2 序列；后续获批索引 + 2 条种子")
    void schemaShapeMatchesDesign() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = current_schema() AND table_type = 'BASE TABLE'", String.class);
        assertThat(tables).containsAll(V15_TABLES);

        List<String> sequences = jdbc.queryForList(
                "SELECT sequencename FROM pg_sequences WHERE schemaname = current_schema()", String.class);
        assertThat(sequences).contains("purchase_order_no_seq", "purchase_receipt_no_seq");

        // 非主键索引：主键走 pg_constraint，不计入这里的计数。部分索引分别来自
        // V22（双入库生命周期）、V40（地图归属，warehouse）、V51（报表日期轴）、V74（采购每日清单）、
        // V78（ADM-05 净需求冻结批次给 purchase_demand 加的「同一批次行只能生成一条需求」唯一索引）
        // 与 V183（录单参考最近采购价：采购单提交时间轴 + 采购行 SKU 有价索引）
        Integer indexes = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes i "
                        + "WHERE i.schemaname = current_schema() "
                        + "  AND i.tablename = ANY (string_to_array(?, ',')) "
                        + "  AND NOT EXISTS (SELECT 1 FROM pg_constraint c "
                        + "                  WHERE c.conname = i.indexname AND c.contype = 'p')",
                Integer.class, V15_TABLE_LIST);
        assertThat(indexes).isEqualTo(40);

        Integer partial = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes i "
                        + "WHERE i.schemaname = current_schema() "
                        + "  AND i.tablename = ANY (string_to_array(?, ',')) "
                        + "  AND i.indexdef LIKE '%WHERE%'", Integer.class, V15_TABLE_LIST);
        // 计数 = V15 建表时的索引 + 后续获批的部分索引（V78 的 ADM-05 批次行唯一索引、
        // V183 的两条最近采购价索引）
        // V190 待定价窗口索引、V192 自动锁单索引）
        // （ON purchase_demand(calculation_batch_item_id)
        // WHERE deleted=FALSE AND calculation_batch_item_id IS NOT NULL）。
        assertThat(partial).isEqualTo(36);

        // 8 条唯一索引 = V15 里显式 CREATE UNIQUE INDEX 的 7 条 + V78（ADM-05 冻结批次）
        // 在 purchase_demand 上加的批次行唯一索引。
        // 必须排除「约束支撑的索引」：PG 的 PRIMARY KEY / UNIQUE 约束也会生成 CREATE UNIQUE INDEX，
        // 不排除就会把 9 个主键算进来，从而把断言变成对主键个数的间接断言。
        Integer unique = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes i "
                        + "WHERE i.schemaname = current_schema() "
                        + "  AND i.tablename = ANY (string_to_array(?, ',')) "
                        + "  AND i.indexdef LIKE 'CREATE UNIQUE%' "
                        + "  AND NOT EXISTS (SELECT 1 FROM pg_constraint c "
                        + "                  WHERE c.conname = i.indexname AND c.contype IN ('p','u'))",
                Integer.class, V15_TABLE_LIST);
        assertThat(unique).isEqualTo(8);

        // 2 条种子：默认仓库（单仓库口径）+ 超收容差配置
        assertThat(jdbc.queryForObject(
                "SELECT status FROM xsy_warehouse WHERE warehouse_code = ? AND deleted = FALSE",
                String.class, SEED_WAREHOUSE_CODE)).isEqualTo("ENABLED");
        assertThat(jdbc.queryForObject(
                "SELECT config_value FROM t_config WHERE config_key = ?",
                String.class, "scm.purchase.over_receipt_tolerance_percent")).isEqualTo("10");
    }

    @Test
    @DisplayName("全库零外键 + 库存表归属 W6/V19 而非 W5（§3 / §8.1）")
    void noForeignKeysAndInventoryTablesBelongToW6() {
        // 全库 FK=0 是 V2 起定下的既有纪律，任何域都不得引入第一个外键。
        // 库存表之间（余额 → 流水）的引用完整性同样走服务层守卫，不走 DB 外键。
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE contype = 'f'", Integer.class)).isZero();

        // 库存表不在采购域的建表范围里，因此这里断言的不是「存在性」，而是「归属」：
        //   1) 它们确实存在；
        //   2) 它们不在 V15 的 9 张采购表里；
        //   3) 表注释带 V19 写入的库存域前缀（证明来自 V19 而不是别处）。
        // 形状（列 / 约束 / 索引）由库存域的 ScmInventoryMigrationIT 负责断言，
        // 本类不重复。
        List<String> inventoryTables = List.of("xsy_inventory_balance", "xsy_inventory_movement");
        assertThat(inventoryTables).doesNotContainAnyElementsOf(V15_TABLES);
        assertThat(jdbc.queryForList(
                "SELECT obj_description(c.oid, 'pg_class') FROM pg_class c "
                        + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE n.nspname = current_schema() AND c.relname = ANY (string_to_array(?, ','))",
                String.class, String.join(",", inventoryTables)))
                .hasSize(2)
                .allSatisfy(comment -> assertThat(comment).startsWith("W6 库存域"));
    }

    @Test
    @DisplayName("Q14：ck_purchase_operation_log_owner 四分支 —— DEMAND_GENERATE 双 NULL 可写、采购单 id 缺失被拒")
    void operationLogOwnerCheckIsOperationTypeAware() {
        // 约束定义必须按 operation_type 分支，而不是「至少一个 id 非空」这种简单写法
        String definition = jdbc.queryForObject(
                "SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c "
                        + "JOIN pg_class t ON t.oid = c.conrelid "
                        + "WHERE t.relname = 'xsy_purchase_operation_log' AND c.conname = 'ck_purchase_operation_log_owner'",
                String.class);
        assertThat(definition)
                .contains("DEMAND_GENERATE")
                .contains("DEMAND_ALLOCATE")
                .contains("RECEIPT_CREATE")
                // 简单写法的特征：「IS NOT NULL OR ... IS NOT NULL」，这种写法必须被拒
                .doesNotContain("purchase_order_id IS NOT NULL OR purchase_receipt_id IS NOT NULL");

        // 正例：DEMAND_GENERATE 发生在采购单还不存在之前，两个 id 必须同时为空才写得进去
        int inserted = jdbc.update(
                "INSERT INTO xsy_purchase_operation_log (purchase_order_id, purchase_receipt_id, operation_type, "
                        + "operator, after_data, created_by) VALUES (NULL, NULL, 'DEMAND_GENERATE', 'W5 IT', "
                        + "'{\"demandIds\":[]}'::JSONB, 'W5 IT')");
        assertThat(inserted).isEqualTo(1);

        // 反例：DEMAND_GENERATE 带采购单 id → 违反归属约束
        expectSqlFailure(
                "INSERT INTO xsy_purchase_operation_log (purchase_order_id, operation_type, operator) "
                        + "VALUES (1, 'DEMAND_GENERATE', 'W5 IT')");
        // 反例：DEMAND_ALLOCATE 没有采购单 id → 违反归属约束
        expectSqlFailure(
                "INSERT INTO xsy_purchase_operation_log (operation_type, operator) "
                        + "VALUES ('DEMAND_ALLOCATE', 'W5 IT')");
        // 反例：RECEIPT_CREATE 只有采购单 id、没有收货单 id → 违反归属约束
        expectSqlFailure(
                "INSERT INTO xsy_purchase_operation_log (purchase_order_id, operation_type, operator) "
                        + "VALUES (1, 'RECEIPT_CREATE', 'W5 IT')");
        // 反例：operation_type 不在白名单
        expectSqlFailure(
                "INSERT INTO xsy_purchase_operation_log (purchase_order_id, operation_type, operator) "
                        + "VALUES (1, 'UNKNOWN_OP', 'W5 IT')");
    }

    @Test
    // 只守性质，不守清单：版本号必须从 1 开始、连续、按应用顺序排列。
    // 早先这里逐条列举到某一个数字，每次新增迁移都要回来补一行，忘了补就是「库已经迁到新版、
    // 测试还在断言旧版」——这类红与业务无关，却让人以为迁移没跑（曾停在 V133，库到 V167）。
    // 真正要守的是「已应用的迁移只追加，不回改、不重排、不漏账」，等价于下面这个连续升序断言。
    @DisplayName("flyway_schema_history：版本连续升序、全部 success，无失败记录")
    void flywayHistoryIsAppendOnly() {
        List<Integer> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history "
                        + "WHERE success = TRUE AND version IS NOT NULL ORDER BY installed_rank",
                Integer.class);
        assertThat(versions).as("迁移记录不应为空，否则本用例是空跑").isNotEmpty();
        int latest = versions.get(versions.size() - 1);
        assertThat(versions)
                .as("已应用的迁移必须从 V1 起连续升序：回改、重排、漏号或插入旧版本都会在这里失败")
                .containsExactlyElementsOf(
                        java.util.stream.IntStream.rangeClosed(1, latest).boxed().toList());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = FALSE", Integer.class)).isZero();
        // 除版本化迁移外，最多只有 1 条 << Flyway Schema Creation >> 基线（version 为空）。
        // 从空 schema 直接 migrate 时没有基线行，因此这里断言的是「不存在别的非版本化记录」。
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version IS NULL", Integer.class))
                .isLessThanOrEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version IS NULL AND description <> '<< Flyway Schema Creation >>'",
                Integer.class)).isZero();
    }
}
