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
        // V22（双入库生命周期）、V40（地图归属，warehouse）、V51（报表日期轴）、V74（采购每日清单）
        // 与 V78（ADM-05 净需求冻结批次给 purchase_demand 加的「同一批次行只能生成一条需求」唯一索引）
        Integer indexes = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes i "
                        + "WHERE i.schemaname = current_schema() "
                        + "  AND i.tablename = ANY (string_to_array(?, ',')) "
                        + "  AND NOT EXISTS (SELECT 1 FROM pg_constraint c "
                        + "                  WHERE c.conname = i.indexname AND c.contype = 'p')",
                Integer.class, V15_TABLE_LIST);
        assertThat(indexes).isEqualTo(36);

        Integer partial = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes i "
                        + "WHERE i.schemaname = current_schema() "
                        + "  AND i.tablename = ANY (string_to_array(?, ',')) "
                        + "  AND i.indexdef LIKE '%WHERE%'", Integer.class, V15_TABLE_LIST);
        // 计数 = V15 建表时的索引 + 后续获批的部分索引，最后一条是 V78 的 ADM-05 批次行唯一索引
        // （ON purchase_demand(calculation_batch_item_id)
        // WHERE deleted=FALSE AND calculation_batch_item_id IS NOT NULL）。
        assertThat(partial).isEqualTo(32);

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
                "SELECT status FROM warehouse WHERE warehouse_code = ? AND deleted = FALSE",
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
                // 简单写法的特征：「IS NOT NULL OR ... IS NOT NULL」，这种写法必须被拒
                .doesNotContain("purchase_order_id IS NOT NULL OR purchase_receipt_id IS NOT NULL");

        // 正例：DEMAND_GENERATE 发生在采购单还不存在之前，两个 id 必须同时为空才写得进去
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
        // 反例：operation_type 不在白名单
        expectSqlFailure(
                "INSERT INTO purchase_operation_log (purchase_order_id, operation_type, operator) "
                        + "VALUES (1, 'UNKNOWN_OP', 'W5 IT')");
    }

    @Test
    // 版本清单逐条列举，而不是只断言 contains：已应用的迁移只追加，新版本只能出现在尾部，
    // 因此任何回改、重排或漏账都会让这个断言立刻失败。
    // 各版本的内容口径：
    //   V17 = 仅数据，t_config 文件上传大小
    //   V18 = 仅数据，t_menu 侧边栏图标
    //   V19 = inventory_balance / inventory_movement 两张库存表
    //         + 历史已确认收货行的期初回填
    //   V20 = 仅数据，t_menu 库存菜单与权限
    //   V21 = 库存流水不可改删触发器
    //   V22 = purchase_receipt 双入库生命周期 + 操作日志类型扩展
    //   V23 = 仅数据，t_menu 入库确认 / 仓库启停权限
    //   V24 = 仅数据，补齐 15 表 + 323 字段 COMMENT，并覆盖 V5 遗留的上游品牌列注释
    //   V25 = SALES_OUT 流水类型 + reserved_quantity + 出库单/预留三张表 + 菜单
    //   V26–V37 = 出库权限 / 日志类型 / 大屏 / 盘点 / 报损报溢 / 调拨 / 预警 / 转换 / 成本 / 导入
    //   V40 = scm_region 省市字典 + 三张主档地理归属列 + 存量地址保守解析
    //   V41 = product_image 删 file_url 列（预签名地址不入库，按 file_key 现算）
    //   V42–V43 = 物流配送：五张配送表 + 地理快照约束；菜单与权限
    //   V44–V45 = product_image 图集分组列；导入导出与图片中心菜单权限
    //   V46 = 仅数据，scm:todo:query 菜单与权限
    //   V47 = delivery_route_order 打印计次三列
    //   V48 = 仅数据，盘点批量导入权限
    //   V49 = image_type 改为 GALLERY/DETAIL，主图唯一事实回归 is_primary
    //   V50 = 仅数据，报表中心菜单与 scm:report:* 权限 1200-1216
    //   V51 = sales_order / order_refund / purchase_receipt 三条报表日期轴部分索引
    //   V63 = 出库行订单来源 + 线路履约状态
    //   V64 = 仅数据，配送三个权限点
    //   V65 = 8 张财务事实表 + 5 条单号序列，零既有表改动
    //   V66 = 仅数据，财务收款登记权限（财务第一个受保护端点）
    //   V67 = 仅数据，付款登记权限
    //   V68 = 仅数据，收款 / 付款反向权限
    //   V69 = 仅数据，核销、反向核销与应付红字权限
    //   V70 = 仅数据，财务查询与导出权限
    //   V71–V72 = 仅数据，五个财务页面菜单与折叠导航图标
    //   V73 = 仅数据，报表中心往来概览页面与查询权限
    //   V74 = report_purchase_daily 每日清单表 + 部分索引
    //   V75 起对应 ADM 系列决策追加的版本（ADM-02/03/04 结算与退货、ADM-12 支付与余额）
    // 没有受保护端点也没有真实页面的一次交付不发布菜单与权限点：财务菜单/权限只随第一次
    // 出现受保护 API 或页面的那次迁移发布，不提前占号，
    // 口径见 docs/plan/active/finance-r1-design.md 的权限矩阵与 Flyway 规划两节。
    //
    // 注意：本用例只读 flyway_schema_history（DB 侧），不扫描磁盘上的迁移文件，
    // 因此它无法发现「文件层重复版本号」这类问题——那需要单独的版本唯一性检查。
    @DisplayName("flyway_schema_history：V1–V114 全部 success，V15–V114 只追加（V1–V14 未被改写）")
    void flywayHistoryIsAppendOnly() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history "
                        + "WHERE success = TRUE AND version IS NOT NULL ORDER BY installed_rank",
                String.class);
        // 逐条列举而不是只断言 contains：V1–V14 一旦被重写/重排，这个断言会立刻失败
        assertThat(versions).containsExactly(
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18",
                "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31", "32", "33", "34", "35",
                "36", "37", "38", "39", "40", "41", "42", "43", "44", "45", "46", "47", "48", "49", "50", "51", "52",
                "53", "54", "55", "56", "57", "58", "59", "60", "61", "62", "63", "64", "65", "66", "67",
                "68", "69", "70", "71", "72", "73", "74",
                // ADM 系列决策追加的版本（ADM-02/03/04 结算与退货、ADM-12 支付与余额）；
                // 同样逐条列举而不是 contains：只增不减，且顺序不变。
                "75", "76", "77", "78", "79", "80", "81", "82", "83", "84", "85", "86", "87", "88", "89", "90",
                "91", "92", "93", "94", "95", "96", "97", "98", "99", "100", "101", "102", "103", "104", "105",
                "106", "107", "108", "109", "110", "111", "112", "113", "114");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = FALSE", Integer.class)).isZero();
        // 除上面逐条列举的版本化迁移外，只有 1 条 << Flyway Schema Creation >> 基线（version 为空）
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version IS NULL", Integer.class)).isEqualTo(1);
    }
}
