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
    @DisplayName("V15 建 9 表 + 2 序列 + 32 索引（28 部分 / 7 唯一）+ 2 条种子")
    void schemaShapeMatchesDesign() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = current_schema() AND table_type = 'BASE TABLE'", String.class);
        assertThat(tables).containsAll(V15_TABLES);

        List<String> sequences = jdbc.queryForList(
                "SELECT sequencename FROM pg_sequences WHERE schemaname = current_schema()", String.class);
        assertThat(sequences).contains("purchase_order_no_seq", "purchase_receipt_no_seq");

        // 非主键索引（主键走 pg_constraint，不计入 §6.4 的 31 条；B1 的 V22 追加 1 条部分索引）
        Integer indexes = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes i "
                        + "WHERE i.schemaname = current_schema() "
                        + "  AND i.tablename = ANY (string_to_array(?, ',')) "
                        + "  AND NOT EXISTS (SELECT 1 FROM pg_constraint c "
                        + "                  WHERE c.conname = i.indexname AND c.contype = 'p')",
                Integer.class, V15_TABLE_LIST);
        assertThat(indexes).isEqualTo(32);

        Integer partial = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes i "
                        + "WHERE i.schemaname = current_schema() "
                        + "  AND i.tablename = ANY (string_to_array(?, ',')) "
                        + "  AND i.indexdef LIKE '%WHERE%'", Integer.class, V15_TABLE_LIST);
        assertThat(partial).isEqualTo(28);

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
    @DisplayName("全库零外键 + 库存表归属 W6/V19 而非 W5（§3 / §8.1）")
    void noForeignKeysAndInventoryTablesBelongToW6() {
        // 全库 FK=0 是 V2 的既有纪律（W1–W4 已实测），W5/W6 都不得引入第一个外键。
        // 库存表之间（余额 → 流水）的引用完整性同样走服务层守卫，不走 DB 外键。
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE contype = 'f'", Integer.class)).isZero();

        // W5 当时断言「一张库存表都不存在」（库存是 W6 的活，W5 不得留迁移债）。
        // W6 的 V19 合法地建了这两张表，因此这里改成断言**归属**：
        //   1) 它们确实存在；
        //   2) 它们不在 V15 的 9 张表里（证明不是 W5 偷偷建的）；
        //   3) 表注释带 V19 的 `W6 库存域` 前缀（证明来自 V19 而不是别处）。
        // 形状（列 / 约束 / 索引）由 W6 的 ScmInventoryMigrationIT 负责断言，本类不重复。
        List<String> inventoryTables = List.of("inventory_balance", "inventory_movement");
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
    // 上限随获批的新迁移追加而抬升。当前上限 23：
    //   V17 = F0（仅数据，t_config 文件上传大小）
    //   V18 = W5.5（仅数据，t_menu 侧边栏图标）
    //   V19 = W6（inventory_balance / inventory_movement + Q5 backfill）
    //   V20 = W6（仅数据，t_menu 库存菜单与权限）
    //   V21 = W6 静态复核修复（流水不可改删触发器）
    //   V22 = B1（purchase_receipt 双入库生命周期 + 操作日志类型扩展）
    //   V23 = B1（仅数据，t_menu 入库确认 / 仓库启停权限）
    // V1–V18 的内容与顺序仍被逐条钉死，任何回改/重排都会立刻失败。
    //
    // 注意：本用例只读 flyway_schema_history（DB 侧），**不扫描磁盘上的 migration 文件**，
    // 因此它无法发现「文件层重复版本号」这类问题——那需要单独的版本唯一性检查。
    @DisplayName("flyway_schema_history：V1–V23 全部 success，V15–V23 只追加（V1–V14 未被改写）")
    void flywayHistoryIsAppendOnly() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history "
                        + "WHERE success = TRUE AND version IS NOT NULL ORDER BY installed_rank",
                String.class);
        // 逐条列举而不是只断言 contains：V1–V14 一旦被重写/重排，这个断言会立刻失败
        assertThat(versions).containsExactly(
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18",
                "19", "20", "21", "22", "23");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = FALSE", Integer.class)).isZero();
        // 除 23 条版本化迁移外，只有 1 条 << Flyway Schema Creation >> 基线（version 为空）
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version IS NULL", Integer.class)).isEqualTo(1);
    }
}
