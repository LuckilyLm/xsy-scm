package net.lab1024.sa.admin.module.scm.inventory;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryBalanceDao;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryMovementDao;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryCommandService;
import net.lab1024.sa.admin.module.scm.inventory.support.PurchaseInventoryContractImpl;
import net.lab1024.sa.admin.module.scm.purchase.support.NoOpPurchaseInventoryContract;
import net.lab1024.sa.admin.module.scm.purchase.support.PurchaseInventoryContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V19 的 schema 形状与 W6 的契约装配（W6 Target Design §12.1 #11 / #17）。
 *
 * <p><b>本类取代了已删除的 {@code PurchaseInventoryContractAbsenceIT}</b>（Q6）：
 * W5 期间该 IT 断言「容器里没有任何库存契约 Bean」（因为 W5 只定义契约、不实现），
 * W6-1 交付真实实现后该前提不再成立，因此按裁决废止它、改由 {@link #exactlyOneInventoryContractBean()}
 * 断言**恰好一个**真实实现 —— 从「没有实现」到「只有一个正确实现」，这才是 W5 契约被兑现的证据。
 * 占位实现 {@code NoOpPurchaseInventoryContract} 仍然保留但继续不注册，理由见其类注释。
 *
 * <p>本类是 W6 **唯一直接断言 schema 形状**的地方（列 / 约束 / 索引 / 索引谓词），
 * 其余 IT 只断言行为。schema 一旦被后续波次悄悄改动，这里会先失败。
 *
 * <p><b>V19/V20 只追加、不可改</b>：本类不修改任何数据，只读元数据 + 用
 * {@code expectSqlFailure}（SAVEPOINT 隔离）验证约束真的会拒绝坏数据。
 */
@DisplayName("W6 库存域迁移与契约装配（PG IT）")
class ScmInventoryMigrationIT extends ScmW6PgITBase {

    private static final List<String> W6_TABLES = List.of("inventory_balance", "inventory_movement");

    /**
     * 出库波次与盘点波次新增的表。
     *
     * <p>与 {@link #W6_TABLES} 分开列，是为了让「W6-1 交付了什么」这个事实不被后续波次稀释 ——
     * 一个列表越加越长就失去了「范围锁定」的作用。
     */
    private static final List<String> POST_W6_TABLES = List.of(
            "inventory_outbound", "inventory_outbound_item", "inventory_reservation",
            "inventory_stocktake", "inventory_stocktake_item",
            "inventory_loss_gain", "inventory_loss_gain_item",
            "inventory_transfer", "inventory_transfer_item",
            "inventory_warning_threshold",
            "inventory_conversion", "inventory_conversion_item");

    /** 一个不可能与真实 id 冲突的哨兵（identity 从 1 起）。 */
    private static final long SENTINEL_SOURCE_ITEM_ID = 9_000_000_000L + (System.nanoTime() % 1_000_000_000L);

    private String constraintDef(String table, String constraint) {
        return jdbc.queryForObject(
                "SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c "
                        + "JOIN pg_class t ON t.oid = c.conrelid "
                        + "WHERE t.relname = ? AND c.conname = ?",
                String.class, table, constraint);
    }

    private List<String> columnsOf(String table) {
        return jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = current_schema() AND table_name = ?",
                String.class, table);
    }

    /** 插一条合法的 PURCHASE_IN 流水，返回 id（用于验证 append-only 与 CHECK）。 */
    private Long insertMovement(String unitSnapshot, String movementType, String quantity,
                               String before, String after) {
        return jdbc.queryForObject(
                "INSERT INTO inventory_movement (warehouse_id, sku_id, movement_type, source_document_type, "
                        + "source_document_id, source_document_item_id, quantity, unit_snapshot, unit_cost, "
                        + "before_quantity, after_quantity, occurred_at, operator, deleted, created_by) "
                        + "VALUES (1, 1, ?, 'PURCHASE_RECEIPT_ITEM', 1, ?, ?::NUMERIC, ?, 1, "
                        + "?::NUMERIC, ?::NUMERIC, CURRENT_TIMESTAMP, 'W6 IT', FALSE, 'W6 IT') RETURNING id",
                Long.class, movementType, SENTINEL_SOURCE_ITEM_ID, quantity, unitSnapshot, before, after);
    }

    // ------------------------------------------------------------------
    // #11
    // ------------------------------------------------------------------

    @Test
    @DisplayName("#11 DDL 契约：零外键、列形状、CHECK 生效、append-only 锁死、部分唯一索引谓词精确")
    void ddlContract() {
        // --- 零外键：V2 的既有纪律（W1–W5 已实测），W6 也不得引入第一个外键 ---
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE contype = 'f'", Integer.class)).isZero();

        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = current_schema() AND table_type = 'BASE TABLE'",
                String.class);
        assertThat(tables).containsAll(W6_TABLES);
        assertThat(tables).containsAll(POST_W6_TABLES);

        // --- 列形状：余额带 unit（Q13）、version，出库波次起带 reserved_quantity；
        //     流水是纯 append-only（无 version / updated_*）---
        assertThat(columnsOf("inventory_balance"))
                .contains("id", "warehouse_id", "sku_id", "unit", "quantity", "reserved_quantity",
                        "version", "deleted",
                        "created_at", "updated_at", "created_by", "updated_by")
                // Q2 / Q3 / Q4 / G-03：这些列被裁决**不纳入 W6-1**，不允许悄悄出现
                .doesNotContain("weight", "avg_cost", "total_cost", "warn_min", "warn_max", "batch_id");
        assertThat(columnsOf("inventory_movement"))
                .contains("id", "warehouse_id", "sku_id", "movement_type", "source_document_type",
                        "source_document_id", "source_document_item_id", "quantity", "unit_snapshot",
                        "unit_cost", "before_quantity", "after_quantity", "occurred_at", "operator",
                        "deleted", "created_at", "created_by")
                .doesNotContain("version", "updated_at", "updated_by");

        // --- 部分唯一索引：谓词必须与 ON CONFLICT 的冲突目标完全一致（Q11）---
        String balanceIndex = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes "
                        + "WHERE schemaname = current_schema() AND indexname = 'uk_inventory_balance_wh_sku_active'",
                String.class);
        assertThat(balanceIndex).containsIgnoringCase("unique");
        assertThat(balanceIndex).contains("warehouse_id, sku_id");
        assertThat(balanceIndex).containsIgnoringCase("deleted = false");

        String movementIndex = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes "
                        + "WHERE schemaname = current_schema() AND indexname = 'uk_inventory_movement_source_active'",
                String.class);
        assertThat(movementIndex).containsIgnoringCase("unique");
        assertThat(movementIndex).contains("source_document_type, source_document_item_id");
        assertThat(movementIndex).containsIgnoringCase("deleted = false");
        assertThat(movementIndex).containsIgnoringCase("source_document_item_id IS NOT NULL");

        // --- CHECK 定义（读约束定义，确认判据写在 DB 里而不是只写在服务层）---
        // 快照约束必须是**方向感知**的：V19 只写了入库分支，出库波次补了出库分支，
        // 盘点波次补盘盈 / 盘亏，报损报溢波次补报损 / 报溢，调拨波次补转出 / 转入 ——
        // 八个类型必须**全部**落在一个方向组里。
        assertThat(constraintDef("inventory_movement", "ck_inventory_movement_snap"))
                .contains("before_quantity").contains("after_quantity").contains("quantity")
                .contains("PURCHASE_IN").contains("SALES_OUT")
                .contains("STOCKTAKE_GAIN").contains("STOCKTAKE_LOSS")
                .contains("LOSS_REPORT").contains("GAIN_REPORT")
                .contains("TRANSFER_OUT").contains("TRANSFER_IN")
                .contains("CONVERT_OUT").contains("CONVERT_IN");
        assertThat(constraintDef("inventory_movement", "ck_inventory_movement_append_only"))
                .containsIgnoringCase("deleted = false");
        assertThat(constraintDef("inventory_movement", "ck_inventory_movement_type"))
                .contains("PURCHASE_IN").contains("SALES_OUT")
                .contains("STOCKTAKE_GAIN").contains("STOCKTAKE_LOSS")
                .contains("LOSS_REPORT").contains("GAIN_REPORT")
                .contains("TRANSFER_OUT").contains("TRANSFER_IN")
                .contains("CONVERT_OUT").contains("CONVERT_IN");
        assertThat(constraintDef("inventory_balance", "ck_inventory_balance_quantity"))
                .contains("quantity");
        // 可用量不为负：已预留的货不能被出库吃掉（出库波次新增）
        assertThat(constraintDef("inventory_balance", "ck_inventory_balance_available"))
                .contains("reserved_quantity").contains("quantity");
        assertThat(constraintDef("inventory_balance", "ck_inventory_balance_reserved"))
                .contains("reserved_quantity");

        // --- 行为验证：约束真的会拒绝坏数据 ---
        Long movementId = insertMovement("kg", "PURCHASE_IN", "5", "0", "5");
        assertThat(movementId).isNotNull();

        // Q7 硬化：soft delete 历史流水在**数据库层**失败（服务层纪律升级为 DB 约束）
        expectSqlFailure("UPDATE inventory_movement SET deleted = TRUE WHERE id = ?", movementId);
        // V21 closes the gap left by CHECK (deleted = FALSE): valid-looking edits
        // and physical removal must also fail, even when every CHECK would pass.
        expectSqlFailure("UPDATE inventory_movement SET unit_cost = 2 WHERE id = ?", movementId);
        expectSqlFailure("UPDATE inventory_movement SET quantity = 6, after_quantity = 6 WHERE id = ?", movementId);
        expectSqlFailure("DELETE FROM inventory_movement WHERE id = ?", movementId);
        expectSqlFailure("TRUNCATE TABLE inventory_movement");
        // 行仍然在，且仍是 deleted = FALSE
        assertThat(jdbc.queryForObject(
                "SELECT deleted FROM inventory_movement WHERE id = ?", Boolean.class, movementId)).isFalse();

        // after ≠ before + quantity → 违反恒等式
        expectSqlFailure("INSERT INTO inventory_movement (warehouse_id, sku_id, movement_type, "
                + "source_document_type, source_document_id, source_document_item_id, quantity, unit_snapshot, "
                + "before_quantity, after_quantity, occurred_at, deleted) "
                + "VALUES (1, 1, 'PURCHASE_IN', 'PURCHASE_RECEIPT_ITEM', 1, ?, 5, 'kg', 0, 6, "
                + "CURRENT_TIMESTAMP, FALSE)", SENTINEL_SOURCE_ITEM_ID + 1);
        // quantity 必须 > 0（方向编码在类型里，数量恒为正）
        expectSqlFailure("INSERT INTO inventory_movement (warehouse_id, sku_id, movement_type, "
                + "source_document_type, source_document_id, source_document_item_id, quantity, unit_snapshot, "
                + "before_quantity, after_quantity, occurred_at, deleted) "
                + "VALUES (1, 1, 'PURCHASE_IN', 'PURCHASE_RECEIPT_ITEM', 1, ?, 0, 'kg', 0, 0, "
                + "CURRENT_TIMESTAMP, FALSE)", SENTINEL_SOURCE_ITEM_ID + 2);
        // movement_type 白名单：**白名单之外一律拒绝**。这里用 UNKNOWN_IN 这个
        // 明确不存在的名字 —— 十个真实类型已全部落地，再拿「未实现的业务类型」当反例
        // 会每落地一个就要改一次（这条断言在 V29/V30/V31/V33 各改过一次）。
        // 也刻意不用已放行的类型：那会因快照方向不符而失败，就不是在验证白名单了。
        expectSqlFailure("INSERT INTO inventory_movement (warehouse_id, sku_id, movement_type, "
                + "source_document_type, source_document_id, source_document_item_id, quantity, unit_snapshot, "
                + "before_quantity, after_quantity, occurred_at, deleted) "
                + "VALUES (1, 1, 'UNKNOWN_IN', 'PURCHASE_RECEIPT_ITEM', 1, ?, 1, 'kg', 0, 1, "
                + "CURRENT_TIMESTAMP, FALSE)", SENTINEL_SOURCE_ITEM_ID + 3);
        // 单位快照不能是空白
        expectSqlFailure("INSERT INTO inventory_movement (warehouse_id, sku_id, movement_type, "
                + "source_document_type, source_document_id, source_document_item_id, quantity, unit_snapshot, "
                + "before_quantity, after_quantity, occurred_at, deleted) "
                + "VALUES (1, 1, 'PURCHASE_IN', 'PURCHASE_RECEIPT_ITEM', 1, ?, 1, '  ', 0, 1, "
                + "CURRENT_TIMESTAMP, FALSE)", SENTINEL_SOURCE_ITEM_ID + 4);
        // Q10：余额数量本期冻结 >= 0
        expectSqlFailure("INSERT INTO inventory_balance (warehouse_id, sku_id, unit, quantity, version, deleted) "
                + "VALUES (1, 1, 'kg', -1, 0, FALSE)");
        // Q13：余额单位不能是空白
        expectSqlFailure("INSERT INTO inventory_balance (warehouse_id, sku_id, unit, quantity, version, deleted) "
                + "VALUES (1, 1, '', 1, 0, FALSE)");

        // --- append-only 在**代码层**的形态：DAO 只声明 insert + select ---
        // 这是「未来冲销必须新增反向 movement，而不是改历史行」的编译期保障：
        // 任何人往这个 DAO 加 update/delete 方法，本断言会立刻失败并强制走评审。
        List<String> movementDaoMethods = Arrays.stream(InventoryMovementDao.class.getDeclaredMethods())
                .map(Method::getName).sorted().toList();
        assertThat(movementDaoMethods)
                .containsExactly("countActiveBySourceItem", "insertOnConflictDoNothing", "queryPage");

        // 余额 DAO 也**没有**任何「设置绝对数量」的方法：余额只能是流水的净和。
        // 出库波次新增 4 个**增量**方法（出库扣减 + 预留增减），仍然没有赋值型方法。
        List<String> balanceDaoMethods = Arrays.stream(InventoryBalanceDao.class.getDeclaredMethods())
                .map(Method::getName).sorted().toList();
        assertThat(balanceDaoMethods).containsExactly(
                "decrementQuantity", "decrementReserved", "detail", "incrementQuantity",
                "incrementReserved", "insertOnConflictDoNothing", "lockByWarehouseAndSku",
                "queryPage", "selectByWarehouseAndSku");
        assertThat(balanceDaoMethods)
                .noneMatch(name -> name.toLowerCase().contains("setquantity"))
                .noneMatch(name -> name.toLowerCase().contains("updatequantity"));
    }

    // ------------------------------------------------------------------
    // V30 报损报溢：四条 DB 层判据
    // ------------------------------------------------------------------

    /**
     * 报损报溢 schema 的四条判据（类型 / 状态 / 原因 / 审核信息）必须在**数据库层**生效。
     *
     * <p>为什么这四条值得单独断言：它们不是「格式约束」，而是**控制**——
     * 类型决定库存往哪个方向变；原因与审核信息是审批留痕的全部内容。
     * 只写在服务层的话，任何绕过服务层的写入（脚本、修数、未来的新入口）都会静默破坏它们。
     */
    @Test
    @DisplayName("V30 报损报溢 schema：类型 / 状态 / 原因 / 审核信息四条判据都在 DB 层生效")
    void lossGainSchemaIsEnforcedByTheDatabase() {
        // 白名单与枚举同源（前端枚举、后端枚举、DB CHECK 三处必须一致）
        assertThat(constraintDef("inventory_loss_gain", "ck_inventory_loss_gain_type"))
                .contains("LOSS").contains("OVERFLOW");
        assertThat(constraintDef("inventory_loss_gain", "ck_inventory_loss_gain_status"))
                .contains("PENDING").contains("COMPLETED").contains("REJECTED");
        assertThat(constraintDef("inventory_loss_gain", "ck_inventory_loss_gain_reason"))
                .contains("reason");
        assertThat(constraintDef("inventory_loss_gain", "ck_inventory_loss_gain_audit"))
                .contains("audited_at").contains("auditor");

        String insert = "INSERT INTO inventory_loss_gain "
                + "(loss_gain_no, warehouse_id, adjust_type, status, reason, audited_at, auditor, version, deleted) "
                + "VALUES (?, 1, ?, ?, ?, %s, %s, 0, FALSE)";

        // 1) 未知类型：方向无法确定，不能猜
        expectSqlFailure(insert.formatted("NULL", "NULL"), "W30-IT-1", "CONVERT", "PENDING", "规格转换不属于本表");
        // 2) 未知状态
        expectSqlFailure(insert.formatted("NULL", "NULL"), "W30-IT-2", "LOSS", "DRAFT", "草稿态不在本表状态机里");
        // 3) 空白原因：没有原因的单据无法审计，「留痕」也就没有内容
        expectSqlFailure(insert.formatted("NULL", "NULL"), "W30-IT-3", "LOSS", "PENDING", "   ");
        // 4) 已完成却没有审核时刻 / 审核人：审批必须留下是谁、什么时候
        expectSqlFailure(insert.formatted("NULL", "NULL"), "W30-IT-4", "LOSS", "COMPLETED", "到货变质");
        // 5) 待审核却带了审核人：两侧都要挡，否则审核信息可以被提前写脏，
        //    「谁批的」就失去意义（因为可以预先填一个名字）
        expectSqlFailure(insert.formatted("CURRENT_TIMESTAMP", "'张三'"), "W30-IT-5", "LOSS", "PENDING", "到货变质");

        // 合法插入必须成功 —— 证明上面五次失败是判据生效，而不是表根本插不进去
        assertThat(jdbc.update(
                "INSERT INTO inventory_loss_gain "
                        + "(loss_gain_no, warehouse_id, adjust_type, status, reason, version, deleted) "
                        + "VALUES (?, 1, 'LOSS', 'PENDING', '到货变质', 0, FALSE)", "W30-IT-OK"))
                .isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // V31 调拨：源仓≠目标仓 + 状态与发出/收货信息一致
    // ------------------------------------------------------------------

    /**
     * 调拨 schema 的四条判据必须在**数据库层**生效。
     *
     * <p>为什么值得单独断言：「源仓 ≠ 目标仓」是调拨唯一的结构性前提，
     * 而「状态与发出 / 收货信息一致」保证了两笔流水各自的 {@code occurred_at} 与
     * {@code operator} 一定读得到 —— 少了任何一条，调拨就能写出无法审计的账。
     */
    @Test
    @DisplayName("V31 调拨 schema：源仓≠目标仓、状态与发出/收货信息一致，都在 DB 层生效")
    void transferSchemaIsEnforcedByTheDatabase() {
        assertThat(constraintDef("inventory_transfer", "ck_inventory_transfer_status"))
                .contains("DRAFT").contains("SHIPPED").contains("RECEIVED").contains("CANCELLED");
        assertThat(constraintDef("inventory_transfer", "ck_inventory_transfer_distinct"))
                .contains("from_warehouse_id").contains("to_warehouse_id");
        assertThat(constraintDef("inventory_transfer", "ck_inventory_transfer_shipped"))
                .contains("shipped_at").contains("shipped_by");
        assertThat(constraintDef("inventory_transfer", "ck_inventory_transfer_received"))
                .contains("received_at").contains("received_by");

        String insert = "INSERT INTO inventory_transfer "
                + "(transfer_no, from_warehouse_id, to_warehouse_id, status, "
                + "shipped_at, shipped_by, received_at, received_by, version, deleted) "
                + "VALUES (?, ?, ?, ?, %s, %s, %s, %s, 0, FALSE)";

        // 1) 源仓 == 目标仓：那不是调拨，是把货在同一行余额上来回加减
        expectSqlFailure(insert.formatted("NULL", "NULL", "NULL", "NULL"),
                "W31-IT-1", 1L, 1L, "DRAFT");
        // 2) 未知状态
        expectSqlFailure(insert.formatted("NULL", "NULL", "NULL", "NULL"),
                "W31-IT-2", 1L, 2L, "CONFIRMED");
        // 3) 在途却没有发出信息 → 转出流水的 occurred_at / operator 就无从取值
        expectSqlFailure(insert.formatted("NULL", "NULL", "NULL", "NULL"),
                "W31-IT-3", 1L, 2L, "SHIPPED");
        // 4) 已收货却没有收货信息 → 转入流水的 occurred_at / operator 无从取值
        expectSqlFailure(insert.formatted("CURRENT_TIMESTAMP", "'甲'", "NULL", "NULL"),
                "W31-IT-4", 1L, 2L, "RECEIVED");
        // 5) 草稿却带了发出人：两侧都要挡，否则「谁发的」可以被预先写脏
        expectSqlFailure(insert.formatted("CURRENT_TIMESTAMP", "'甲'", "NULL", "NULL"),
                "W31-IT-5", 1L, 2L, "DRAFT");

        // 合法插入必须成功 —— 证明上面五次失败是判据生效，而不是表根本插不进去
        assertThat(jdbc.update("INSERT INTO inventory_transfer "
                + "(transfer_no, from_warehouse_id, to_warehouse_id, status, version, deleted) "
                + "VALUES (?, 1, 2, 'DRAFT', 0, FALSE)", "W31-IT-OK")).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // V32 预警阈值：配置不在余额表上 + 区间判据
    // ------------------------------------------------------------------

    /**
     * V32 的两条结构性事实。
     *
     * <p>第一条是本波次最重要的取舍：**阈值配置不在 {@code inventory_balance} 上**。
     * 上面 {@link #ddlContract()} 里 {@code doesNotContain("warn_min", "warn_max")} 的断言
     * 从 W6-1 起就在，本波次**没有削弱它** —— 它现在的含义更明确了：
     * 预警配置刻意不在余额表上，因为余额行只能由流水产生，而配置路径不该造出
     * 「没有流水支撑的余额行」。
     */
    @Test
    @DisplayName("V32 预警阈值：配置表独立于余额表，且区间判据在 DB 层生效")
    void warningThresholdSchemaIsEnforcedByTheDatabase() {
        // 阈值配置表存在，且余额表上**依然**没有 warn_min / warn_max
        assertThat(columnsOf("inventory_warning_threshold"))
                .contains("warehouse_id", "sku_id", "warn_min", "warn_max", "version", "deleted");
        assertThat(columnsOf("inventory_balance"))
                .as("阈值是配置，不是余额状态：不得挪到余额表上")
                .doesNotContain("warn_min", "warn_max");

        // 一个 (仓库, SKU) 只能有一条有效配置
        String index = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = current_schema() "
                        + "AND indexname = 'uk_inventory_warning_threshold_wh_sku_active'",
                String.class);
        assertThat(index).containsIgnoringCase("unique");
        assertThat(index).contains("warehouse_id, sku_id");
        assertThat(index).containsIgnoringCase("deleted = false");

        assertThat(constraintDef("inventory_warning_threshold", "ck_inventory_warning_threshold_range"))
                .contains("warn_min").contains("warn_max");

        String insert = "INSERT INTO inventory_warning_threshold "
                + "(warehouse_id, sku_id, warn_min, warn_max, version, deleted) "
                + "VALUES (1, 1, %s, %s, 0, FALSE)";

        // 注意：下面每条 SQL 都**不带参数占位符**（边界值已经 format 进 SQL 文本），
        // 所以不能再给 expectSqlFailure 传参数 —— 传了会让它因为「栏位索引超过许可范围」
        // 而失败，看起来像「约束生效」，实际上根本没测到约束。
        // 1) 上下限都没有：没有任何判断依据
        expectSqlFailure(insert.formatted("NULL", "NULL"));
        // 2) 下限为负
        expectSqlFailure(insert.formatted("-1", "NULL"));
        // 3) 上限为负
        expectSqlFailure(insert.formatted("NULL", "-1"));
        // 4) 下限大于上限：会让所有状态都异常，预警失去意义
        expectSqlFailure(insert.formatted("10", "5"));

        // 合法插入必须成功。两条用**不同的 sku_id** —— 唯一索引是 (warehouse_id, sku_id)，
        // 同一个 sku 插两次会撞索引，那是另一条断言（下面 V32 的唯一索引已单独验证过）。
        assertThat(jdbc.update(insert.formatted("5", "100"))).isEqualTo(1);
        assertThat(jdbc.update("INSERT INTO inventory_warning_threshold "
                + "(warehouse_id, sku_id, warn_min, warn_max, version, deleted) "
                + "VALUES (1, 2, 5, 5, 0, FALSE)"))
                .as("上下限相等是合法的（等价于精确值告警）").isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // #17
    // ------------------------------------------------------------------

    @Test
    @DisplayName("#17 契约装配：PurchaseInventoryContract 恰好 1 个 Bean（真实实现），NoOp 未注册")
    void exactlyOneInventoryContractBean() {
        // Q6：PurchaseInventoryContractAbsenceIT 已废止，改由本断言接替 ——
        // 「容器里恰好一个库存契约 Bean」是 W5 契约真正被兑现的充要条件。
        Map<String, PurchaseInventoryContract> beans =
                applicationContext.getBeansOfType(PurchaseInventoryContract.class);
        assertThat(beans).hasSize(1);
        assertThat(beans.values().iterator().next())
                .isInstanceOf(PurchaseInventoryContractImpl.class);

        // W5 的占位实现仍然存在（保留「purchase 侧零改动」这一可审计事实）但**没有**被注册：
        // 否则会出现两个候选 Bean，装配期就会歧义报错 —— 那也是一种失败，只是失败得很晚。
        assertThat(applicationContext.getBeansOfType(NoOpPurchaseInventoryContract.class)).isEmpty();

        // 写路径必须**加入调用方事务**，因此命令服务不得自带事务语义。
        // 加 @Transactional(REQUIRES_NEW) 会让「收货确认与入库同事务」静默失效，
        // 而那种失效在功能测试里看不出来（入库照样成功），只有 #13 的回滚用例会暴露它。
        assertThat(InventoryCommandService.class.getAnnotation(Transactional.class)).isNull();
        assertThat(Arrays.stream(InventoryCommandService.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("postPurchaseInbound"))
                .findFirst()
                .orElseThrow()
                .getAnnotation(Transactional.class)).isNull();
    }
}
