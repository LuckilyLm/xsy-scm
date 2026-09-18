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

        // --- 列形状：余额带 unit（Q13）与 version；流水是纯 append-only（无 version / updated_*）---
        assertThat(columnsOf("inventory_balance"))
                .contains("id", "warehouse_id", "sku_id", "unit", "quantity", "version", "deleted",
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
        assertThat(constraintDef("inventory_movement", "ck_inventory_movement_snap"))
                .contains("before_quantity").contains("after_quantity").contains("quantity");
        assertThat(constraintDef("inventory_movement", "ck_inventory_movement_append_only"))
                .containsIgnoringCase("deleted = false");
        assertThat(constraintDef("inventory_movement", "ck_inventory_movement_type"))
                .contains("PURCHASE_IN");
        assertThat(constraintDef("inventory_balance", "ck_inventory_balance_quantity"))
                .contains("quantity");

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
        // movement_type 白名单（W6-1 只有 PURCHASE_IN；新增类型必须走新迁移扩白名单）
        expectSqlFailure("INSERT INTO inventory_movement (warehouse_id, sku_id, movement_type, "
                + "source_document_type, source_document_id, source_document_item_id, quantity, unit_snapshot, "
                + "before_quantity, after_quantity, occurred_at, deleted) "
                + "VALUES (1, 1, 'SALES_OUT', 'PURCHASE_RECEIPT_ITEM', 1, ?, 1, 'kg', 0, 1, "
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

        // 余额 DAO 也**没有**任何「设置绝对数量」的方法：余额只能是流水的净和
        List<String> balanceDaoMethods = Arrays.stream(InventoryBalanceDao.class.getDeclaredMethods())
                .map(Method::getName).sorted().toList();
        assertThat(balanceDaoMethods).containsExactly("detail", "incrementQuantity",
                "insertOnConflictDoNothing", "lockByWarehouseAndSku", "queryPage", "selectByWarehouseAndSku");
        assertThat(balanceDaoMethods)
                .noneMatch(name -> name.toLowerCase().contains("setquantity"))
                .noneMatch(name -> name.toLowerCase().contains("updatequantity"));
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
