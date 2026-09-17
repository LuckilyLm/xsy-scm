package net.lab1024.sa.admin.module.scm.purchase;

import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V15 / V16 迁移验收（W5 Target Design §11.2，4 例）。
 *
 * <p>本类是**唯一直接断言 schema 形状**的地方（表 / 索引 / 序列 / 种子 / 外键 / 库存表 / CHECK），
 * 其余 IT 只断言行为。schema 一旦被后续波次悄悄改动，这里会先失败。
 *
 * <p><b>V1–V12 永久冻结、V15/V16 只追加</b>：本类不修改任何数据，只读元数据 + 断言约束生效。
 */
@DisplayName("W5 采购域迁移（PG IT）")
class ScmPurchaseMigrationIT extends ScmW5PgITBase {

    /** V15 新建的 9 张表。 */
    private static final List<String> V15_TABLES = List.of(
            "warehouse",
            "purchase_demand",
            "purchase_demand_allocation",
            "purchase_order",
            "purchase_order_item",
            "purchase_receipt",
            "purchase_receipt_item",
            "receipt_weighing_record",
            "purchase_operation_log");

    /**
     * 逗号分隔的表名清单，配合 {@code string_to_array(?, ',')} 使用。
     *
     * <p>**为什么不直接传 {@code String[]}**：{@code JdbcTemplate.queryForObject(sql, Class, Object...)}
     * 会把数组当成**可变参数展开**（9 个表名 → 9 个参数），而 SQL 里只有 1 个 {@code ?}，
     * 于是报「column index out of range」。拼成字符串再在 SQL 里切开，行为与驱动无关。
     */
    private static final String V15_TABLE_LIST = String.join(",", V15_TABLES);

    @Test
    @DisplayName("V15 建 9 表 + 2 序列 + 31 索引（27 部分 / 7 唯一）+ 2 条种子")
    void schemaShapeMatchesDesign() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = current_schema() AND table_type = 'BASE TABLE'", String.class);
        assertThat(tables).containsAll(V15_TABLES);

        List<String> sequences = jdbc.queryForList(
                "SELECT sequencename FROM pg_sequences WHERE schemaname = current_schema()", String.class);
        assertThat(sequences).contains("purchase_order_no_seq", "purchase_receipt_no_seq");

        // 非主键索引（主键走 pg_constraint，不计入 §6.4 的 31 条）
        Integer indexes = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes i "
                        + "WHERE i.schemaname = current_schema() "
                        + "  AND i.tablename = ANY (string_to_array(?, ',')) "
                        + "  AND NOT EXISTS (SELECT 1 FROM pg_constraint c "
                        + "                  WHERE c.conname = i.indexname AND c.contype = 'p')",
                Integer.class, V15_TABLE_LIST);
        assertThat(indexes).isEqualTo(31);

        Integer partial = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes i "
                        + "WHERE i.schemaname = current_schema() "
                        + "  AND i.tablename = ANY (string_to_array(?, ',')) "
                        + "  AND i.indexdef LIKE '%WHERE%'", Integer.class, V15_TABLE_LIST);
        assertThat(partial).isEqualTo(27);

        // 7 条唯一索引 = V15 里显式 CREATE UNIQUE INDEX 的 7 条。
        // 必须排除「约束支撑的索引」：PG 的 PRIMARY KEY / UNIQUE 约束也会生成 CREATE UNIQUE INDEX，
        // 不排除就会把 9 个主键算进来（实测 16），从而把断言变成对主键个数的间接断言。
        Integer unique = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes i "
                        + "WHERE i.schemaname = current_schema() "
                        + "  AND i.tablename = ANY (string_to_array(?, ',')) "
                        + "  AND i.indexdef LIKE 'CREATE UNIQUE%' "
                        + "  AND NOT EXISTS (SELECT 1 FROM pg_constraint c "
                        + "                  WHERE c.conname = i.indexname AND c.contype IN ('p','u'))",
                Integer.class, V15_TABLE_LIST);
        assertThat(unique).isEqualTo(7);

        // 2 条种子：默认仓库（G-03 单仓库）+ 超收容差配置（Q3a）
        assertThat(jdbc.queryForObject(
                "SELECT status FROM warehouse WHERE warehouse_code = ? AND deleted = FALSE",
                String.class, SEED_WAREHOUSE_CODE)).isEqualTo("ENABLED");
        assertThat(jdbc.queryForObject(
                "SELECT config_value FROM t_config WHERE config_key = ?",
                String.class, "scm.purchase.over_receipt_tolerance_percent")).isEqualTo("10");
    }

    @Test
    @DisplayName("全库零外键 + W5 无任何库存表（§3 / §8.1）")
    void noForeignKeysAndNoInventoryTables() {
        // 全库 FK=0 是 V2 的既有纪律（W1–W4 已实测），W5 不得引入第一个外键
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE contype = 'f'", Integer.class)).isZero();

        // 库存域在 W6：W5 不建任何库存表，连「临时余额表」都不允许（否则成为 W6 的迁移债）
        List<String> inventoryLike = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = current_schema() AND ("
                        + "  table_name LIKE 'inventory%' OR table_name LIKE '%_inventory' "
                        + "  OR table_name LIKE '%_movement%' OR table_name LIKE '%_stock%' "
                        + "  OR table_name LIKE '%_balance%')", String.class);
        assertThat(inventoryLike).isEmpty();
    }

    @Test
    @DisplayName("Q14：ck_purchase_operation_log_owner 四分支 —— DEMAND_GENERATE 双 NULL 可写、采购单 id 缺失被拒")
    void operationLogOwnerCheckIsOperationTypeAware() {
        // 约束定义必须按 operation_type 分支，而不是「至少一个 id 非空」这种简单写法
        String definition = jdbc.queryForObject(
                "SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c "
                        + "JOIN pg_class t ON t.oid = c.conrelid "
                        + "WHERE t.relname = 'purchase_operation_log' AND c.conname = 'ck_purchase_operation_log_owner'",
                String.class);
        assertThat(definition)
                .contains("DEMAND_GENERATE")
                .contains("DEMAND_ALLOCATE")
                .contains("RECEIPT_CREATE")
                // 简单写法的特征：`IS NOT NULL OR ... IS NOT NULL`（Q14 明令禁止）
                .doesNotContain("purchase_order_id IS NOT NULL OR purchase_receipt_id IS NOT NULL");

        // 正例：DEMAND_GENERATE 的两个 id 必须**同时为空**（A 源的 NOT NULL 在这里会直接写不进去）
        int inserted = jdbc.update(
                "INSERT INTO purchase_operation_log (purchase_order_id, purchase_receipt_id, operation_type, "
                        + "operator, after_data, created_by) VALUES (NULL, NULL, 'DEMAND_GENERATE', 'W5 IT', "
                        + "'{\"demandIds\":[]}'::JSONB, 'W5 IT')");
        assertThat(inserted).isEqualTo(1);

        // 反例：DEMAND_GENERATE 带采购单 id → 违反归属约束
        expectSqlFailure(
                "INSERT INTO purchase_operation_log (purchase_order_id, operation_type, operator) "
                        + "VALUES (1, 'DEMAND_GENERATE', 'W5 IT')");
        // 反例：DEMAND_ALLOCATE 没有采购单 id → 违反归属约束
        expectSqlFailure(
                "INSERT INTO purchase_operation_log (operation_type, operator) "
                        + "VALUES ('DEMAND_ALLOCATE', 'W5 IT')");
        // 反例：RECEIPT_CREATE 只有采购单 id、没有收货单 id → 违反归属约束
        expectSqlFailure(
                "INSERT INTO purchase_operation_log (purchase_order_id, operation_type, operator) "
                        + "VALUES (1, 'RECEIPT_CREATE', 'W5 IT')");
        // 反例：operation_type 不在白名单（修 A-D16）
        expectSqlFailure(
                "INSERT INTO purchase_operation_log (purchase_order_id, operation_type, operator) "
                        + "VALUES (1, 'UNKNOWN_OP', 'W5 IT')");
    }

    @Test
    // 上限随获批的新迁移追加而抬升。当前上限 18：
    //   V17 = F0（仅数据，t_config 文件上传大小）
    //   V18 = W5.5（仅数据，t_menu 侧边栏图标）
    // V1–V16 的内容与顺序仍被逐条钉死，任何回改/重排都会立刻失败。
    //
    // 注意：本用例只读 flyway_schema_history（DB 侧），**不扫描磁盘上的 migration 文件**，
    // 因此它无法发现「文件层重复版本号」这类问题——那需要单独的版本唯一性检查。
    @DisplayName("flyway_schema_history：V1–V18 全部 success，V15–V18 只追加（V1–V14 未被改写）")
    void flywayHistoryIsAppendOnly() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history "
                        + "WHERE success = TRUE AND version IS NOT NULL ORDER BY installed_rank",
                String.class);
        // 逐条列举而不是只断言 contains：V1–V14 一旦被重写/重排，这个断言会立刻失败
        assertThat(versions).containsExactly(
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = FALSE", Integer.class)).isZero();
        // 除 18 条版本化迁移外，只有 1 条 << Flyway Schema Creation >> 基线（version 为空）
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version IS NULL", Integer.class)).isEqualTo(1);
    }
}
