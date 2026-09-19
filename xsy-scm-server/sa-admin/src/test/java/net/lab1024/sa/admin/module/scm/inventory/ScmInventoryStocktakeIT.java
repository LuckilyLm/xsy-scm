package net.lab1024.sa.admin.module.scm.inventory;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.inventory.domain.InventoryOutboundFact;
import net.lab1024.sa.admin.module.scm.inventory.domain.InventoryStocktakeAdjustment;
import net.lab1024.sa.admin.module.scm.inventory.domain.InventoryStocktakeFact;
import net.lab1024.sa.admin.module.scm.inventory.domain.ReserveInventoryFact;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryBalanceEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryStocktakeAddForm;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryReservationService;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryStocktakeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 盘点的 PostgreSQL 集成测试（盘点波次）。
 *
 * <p>覆盖四件在单测里验证不了的事：
 * <ol>
 *   <li><b>方向感知的快照约束扩到四个分支</b> —— 盘盈 {@code after = before + quantity}、
 *       盘亏 {@code after = before - quantity}，这是 V29 重建
 *       {@code ck_inventory_movement_snap} 的直接原因；</li>
 *   <li><b>差异施加到「确认瞬间的账面量」而不是快照</b> —— 保存草稿到确认之间发生的
 *       收货 / 出库必须被保留，这是本波次最核心的口径；</li>
 *   <li><b>Q10 / 预留的双重下限</b> —— 盘亏不得把库存推成负数，也不得吃掉已预留的货；</li>
 *   <li><b>append-only 对盘点同样生效</b> —— 新增流水类型不是绕过 Q7 的口子。</li>
 * </ol>
 */
@DisplayName("盘点（PG IT）")
class ScmInventoryStocktakeIT extends ScmW6PgITBase {

    @Autowired
    private InventoryStocktakeService stocktakeService;

    @Autowired
    private InventoryReservationService reservations;

    /** 造一个已入库指定数量的 SKU，返回 {@code (warehouseId, skuId)}。 */
    private Object[] stocked(String suffix, String quantity) {
        Long skuId = newSkuOfType(suffix, "NON_STANDARD", "ON_SHELF");
        W6Fixture fixture = inboundFixture(suffix, skuId, quantity);
        confirmReceipt(fixture.receipt().getId(), quantity);
        return new Object[]{seedWarehouseId(), skuId};
    }

    private InventoryStocktakeAddForm form(Long warehouseId, Long skuId, String actual) {
        InventoryStocktakeAddForm form = new InventoryStocktakeAddForm();
        form.setWarehouseId(warehouseId);
        InventoryStocktakeAddForm.Item item = new InventoryStocktakeAddForm.Item();
        item.setSkuId(skuId);
        item.setActualQuantity(new BigDecimal(actual));
        form.setItems(new ArrayList<>(List.of(item)));
        return form;
    }

    private InventoryOutboundFact outboundFact(Long wh, Long sku, String qty, long itemId) {
        return new InventoryOutboundFact(wh, sku, 0L, itemId, new BigDecimal(qty), null,
                OffsetDateTime.now(), "test:1");
    }

    /** 某个 (仓库, SKU) 的盘点流水（盘盈 + 盘亏），按业务时刻升序。 */
    private List<Map<String, Object>> stocktakeMovements(Long wh, Long sku) {
        return movementsOf(wh, sku).stream()
                .filter(m -> String.valueOf(m.get("movement_type")).startsWith("STOCKTAKE_"))
                .toList();
    }

    private static BigDecimal decimal(Map<String, Object> row, String column) {
        return new BigDecimal(String.valueOf(row.get(column)));
    }

    // ------------------------------------------------------------------
    // 盘盈 / 盘亏 / 账实相符
    // ------------------------------------------------------------------

    @Test
    @DisplayName("盘盈：实盘高于账面 → 余额增加并写 STOCKTAKE_GAIN，快照方向为加")
    void gainIncreasesBalanceAndWritesInboundSnapshot() {
        Object[] s = stocked("st1", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        stocktakeService.confirm(stocktakeService.create(form(wh, sku, "12.0000")));

        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("12.0000");

        List<Map<String, Object>> rows = stocktakeMovements(wh, sku);
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.getFirst();
        assertThat(row.get("movement_type")).isEqualTo("STOCKTAKE_GAIN");
        assertThat(row.get("source_document_type")).isEqualTo("STOCKTAKE_ITEM");
        assertThat(decimal(row, "quantity")).isEqualByComparingTo("2.0000");
        // 方向感知快照：盘盈是加，这正是 V29 必须重建 ck_inventory_movement_snap 的原因
        assertThat(decimal(row, "before_quantity")).isEqualByComparingTo("10.0000");
        assertThat(decimal(row, "after_quantity")).isEqualByComparingTo("12.0000");
        // 单位以余额记账单位为准（Q13），由服务端取，不由调用方传
        assertThat(String.valueOf(row.get("unit_snapshot"))).isEqualTo(balanceRow(wh, sku).getUnit());
        // 盘盈没有成本依据，unit_cost 留空（ck_inventory_movement_cost 允许 NULL）
        assertThat(row.get("unit_cost")).isNull();
    }

    @Test
    @DisplayName("盘亏：实盘低于账面 → 余额减少并写 STOCKTAKE_LOSS，快照方向为减")
    void lossDecreasesBalanceAndWritesOutboundSnapshot() {
        Object[] s = stocked("st2", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        stocktakeService.confirm(stocktakeService.create(form(wh, sku, "8.0000")));

        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("8.0000");

        Map<String, Object> row = stocktakeMovements(wh, sku).getFirst();
        assertThat(row.get("movement_type")).isEqualTo("STOCKTAKE_LOSS");
        assertThat(decimal(row, "quantity")).isEqualByComparingTo("2.0000");
        assertThat(decimal(row, "before_quantity")).isEqualByComparingTo("10.0000");
        assertThat(decimal(row, "after_quantity")).isEqualByComparingTo("8.0000");
    }

    @Test
    @DisplayName("账实相符：差异为 0 时不写流水，但单位快照仍回写")
    void zeroDeltaWritesNoMovementButStillStampsUnit() {
        Object[] s = stocked("st3", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        Long id = stocktakeService.create(form(wh, sku, "10.0000"));
        stocktakeService.confirm(id);

        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("10.0000");
        // 只有入库那一条流水，没有盘点流水 —— quantity 恒为正，写不出「零差异」流水
        assertThat(stocktakeMovements(wh, sku)).isEmpty();
        assertThat(movementCount(wh, sku)).isEqualTo(1);

        // 差异为 0 的行仍然回写单位快照（确认动作本身走完了）
        String unit = jdbc.queryForObject(
                "SELECT unit_snapshot FROM inventory_stocktake_item WHERE stocktake_id = ?",
                String.class, id);
        assertThat(unit).isEqualTo(balanceRow(wh, sku).getUnit());
    }

    // ------------------------------------------------------------------
    // 核心口径：差异施加到「确认瞬间的账面量」
    // ------------------------------------------------------------------

    @Test
    @DisplayName("核心口径：差异施加到确认瞬间的账面量，保存草稿后的出库不会被抹掉")
    void deltaIsAppliedToCurrentOnHandNotToTheSnapshot() {
        Object[] s = stocked("st4", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        // 保存草稿：账面量快照 = 10，实盘 12 → 清点发现的差异 delta = +2
        Long id = stocktakeService.create(form(wh, sku, "12.0000"));

        // 确认之前发生一次出库：确认瞬间的账面量变成 7
        inventoryCommandService.postSalesOutbound(outboundFact(wh, sku, "3.0000", 910001L));
        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("7.0000");

        stocktakeService.confirm(id);

        // after = live + delta = 7 + 2 = 9。
        // 若实现是「把账面直接改写成实盘数」，这里会得到 12，那笔出库就被悄悄抹掉了。
        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("9.0000");

        Map<String, Object> row = stocktakeMovements(wh, sku).getFirst();
        assertThat(row.get("movement_type")).isEqualTo("STOCKTAKE_GAIN");
        assertThat(decimal(row, "quantity")).as("差异 = 实盘 − 账面快照").isEqualByComparingTo("2.0000");
        assertThat(decimal(row, "before_quantity")).as("before 是确认瞬间的账面量").isEqualByComparingTo("7.0000");
        assertThat(decimal(row, "after_quantity")).isEqualByComparingTo("9.0000");
    }

    // ------------------------------------------------------------------
    // 下限：Q10 与预留
    // ------------------------------------------------------------------

    @Test
    @DisplayName("盘亏把库存推成负数被拒（41024），余额不变 —— 不引入负库存")
    void lossThatWouldGoNegativeIsRejected() {
        Object[] s = stocked("st5", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        // 账面快照 10、实盘 0 → delta = −10；确认前出库 6，live = 4 → after = −6
        Long id = stocktakeService.create(form(wh, sku, "0.0000"));
        inventoryCommandService.postSalesOutbound(outboundFact(wh, sku, "6.0000", 910002L));

        expectCode(() -> stocktakeService.confirm(id), 41024);
        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("4.0000");
        assertThat(stocktakeMovements(wh, sku)).isEmpty();
    }

    @Test
    @DisplayName("盘亏到低于已预留量被拒（41025）—— 已预留的货不能被盘点吃掉")
    void lossBelowReservedIsRejected() {
        Object[] s = stocked("st6", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        reservations.reserve(new ReserveInventoryFact(
                wh, sku, "SALES_ORDER_ITEM", 710001L, 810001L,
                new BigDecimal("8.0000"), OffsetDateTime.now(), null));

        // 账面快照 10、实盘 7 → delta = −3 → after = 7 < 已预留 8
        expectCode(() -> stocktakeService.confirm(stocktakeService.create(form(wh, sku, "7.0000"))), 41025);

        InventoryBalanceEntity balance = balanceRow(wh, sku);
        assertThat(balance.getQuantity()).isEqualByComparingTo("10.0000");
        assertThat(balance.getReservedQuantity()).isEqualByComparingTo("8.0000");
        assertThat(stocktakeMovements(wh, sku)).isEmpty();
    }

    @Test
    @DisplayName("盘盈不改预留量：盘点只动 quantity，不动 reserved_quantity")
    void gainLeavesReservedUntouched() {
        Object[] s = stocked("st7", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        reservations.reserve(new ReserveInventoryFact(
                wh, sku, "SALES_ORDER_ITEM", 710002L, 810002L,
                new BigDecimal("4.0000"), OffsetDateTime.now(), null));

        stocktakeService.confirm(stocktakeService.create(form(wh, sku, "12.0000")));

        InventoryBalanceEntity balance = balanceRow(wh, sku);
        assertThat(balance.getQuantity()).isEqualByComparingTo("12.0000");
        assertThat(balance.getReservedQuantity()).isEqualByComparingTo("4.0000");
    }

    // ------------------------------------------------------------------
    // 入口校验
    // ------------------------------------------------------------------

    @Test
    @DisplayName("从未入库的 (仓库, SKU) 盘点被拒（41023），且不会建出零余额行")
    void stocktakeWithoutAnyBalanceIsRejectedWithoutCreatingRow() {
        Long skuId = newSkuOfType("st8", "NON_STANDARD", "ON_SHELF");
        Long wh = seedWarehouseId();
        assertThat(balanceRowCount(wh, skuId)).isZero();

        // 记账单位只能来自余额行，因此「凭空盘盈」必须被挡住
        expectCode(() -> stocktakeService.create(form(wh, skuId, "5.0000")), 41023);
        assertThat(balanceRowCount(wh, skuId)).isZero();
    }

    @Test
    @DisplayName("同一 SKU 在盘点单里出现两次被拒（41027）—— 差异不得被施加两次")
    void duplicateSkuInOneStocktakeIsRejected() {
        Object[] s = stocked("st9", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        InventoryStocktakeAddForm form = form(wh, sku, "12.0000");
        InventoryStocktakeAddForm.Item second = new InventoryStocktakeAddForm.Item();
        second.setSkuId(sku);
        second.setActualQuantity(new BigDecimal("13.0000"));
        form.getItems().add(second);

        expectCode(() -> stocktakeService.create(form), 41027);
        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("10.0000");
    }

    @Test
    @DisplayName("已确认的盘点单不可再确认、不可再改（41020），余额不会被调整两次")
    void confirmedStocktakeIsTerminal() {
        Object[] s = stocked("st10", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        Long id = stocktakeService.create(form(wh, sku, "12.0000"));
        stocktakeService.confirm(id);
        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("12.0000");

        expectCode(() -> stocktakeService.confirm(id), 41020);
        expectCode(() -> stocktakeService.update(id, form(wh, sku, "20.0000")), 41020);
        expectCode(() -> stocktakeService.delete(id), 41020);

        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("12.0000");
        assertThat(stocktakeMovements(wh, sku)).hasSize(1);
    }

    @Test
    @DisplayName("草稿取消不产生任何库存影响，且不可再确认")
    void cancelledDraftHasNoStockEffect() {
        Object[] s = stocked("st11", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        Long id = stocktakeService.create(form(wh, sku, "15.0000"));
        stocktakeService.cancel(id);

        expectCode(() -> stocktakeService.confirm(id), 41020);
        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("10.0000");
        assertThat(stocktakeMovements(wh, sku)).isEmpty();
    }

    @Test
    @DisplayName("改草稿会重新快照账面量：基线跟着账面走，不冻结在建单那一刻")
    void updatingDraftRefreshesTheBookSnapshot() {
        Object[] s = stocked("st12", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        Long id = stocktakeService.create(form(wh, sku, "10.0000"));

        // 账面变成 6 之后重存草稿 → 基线刷新为 6，实盘 10 的差异变成 +4
        inventoryCommandService.postSalesOutbound(outboundFact(wh, sku, "4.0000", 910003L));
        stocktakeService.update(id, form(wh, sku, "10.0000"));

        stocktakeService.confirm(id);
        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("10.0000");
        assertThat(decimal(stocktakeMovements(wh, sku).getFirst(), "quantity")).isEqualByComparingTo("4.0000");
    }

    // ------------------------------------------------------------------
    // 命令侧契约
    // ------------------------------------------------------------------

    @Test
    @DisplayName("同一来源行重复盘点被拒（41026），不会调整两次")
    void duplicateStocktakeFromSameSourceLineIsRejected() {
        Object[] s = stocked("st13", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        InventoryStocktakeFact fact = new InventoryStocktakeFact(
                wh, sku, 0L, 920001L, new BigDecimal("10.0000"), new BigDecimal("12.0000"),
                OffsetDateTime.now(), "test:1");
        InventoryStocktakeAdjustment first = inventoryCommandService.postStocktakeAdjust(fact);
        assertThat(first.movementWritten()).isTrue();
        assertThat(first.delta()).isEqualByComparingTo("2.0000");

        expectCode(() -> inventoryCommandService.postStocktakeAdjust(fact), 41026);
        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("12.0000");
        assertThat(stocktakeMovements(wh, sku)).hasSize(1);
    }

    @Test
    @DisplayName("差异为 0 时命令侧返回 movementWritten = false（不写流水）")
    void zeroDeltaCommandReportsNoMovement() {
        Object[] s = stocked("st14", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        InventoryStocktakeAdjustment result = inventoryCommandService.postStocktakeAdjust(
                new InventoryStocktakeFact(wh, sku, 0L, 920002L, new BigDecimal("10.0000"),
                        new BigDecimal("10.0000"), OffsetDateTime.now(), "test:1"));

        assertThat(result.movementWritten()).isFalse();
        assertThat(result.delta()).isEqualByComparingTo("0.0000");
        assertThat(result.beforeQuantity()).isEqualByComparingTo("10.0000");
        assertThat(result.afterQuantity()).isEqualByComparingTo("10.0000");
        assertThat(stocktakeMovements(wh, sku)).isEmpty();
    }

    @Test
    @DisplayName("Q7：盘点流水同样不可改删（新增类型不是绕过 append-only 的口子）")
    void stocktakeMovementsAreStillAppendOnly() {
        Object[] s = stocked("st15", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        stocktakeService.confirm(stocktakeService.create(form(wh, sku, "8.0000")));

        Long movementId = ((Number) stocktakeMovements(wh, sku).getFirst().get("id")).longValue();
        expectSqlFailure("UPDATE inventory_movement SET deleted = TRUE WHERE id = ?", movementId);
        expectSqlFailure("UPDATE inventory_movement SET quantity = 1, after_quantity = 9 WHERE id = ?", movementId);
        expectSqlFailure("DELETE FROM inventory_movement WHERE id = ?", movementId);
    }
}
