package net.lab1024.sa.admin.module.scm.inventory;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.inventory.domain.ReserveInventoryFact;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryStocktakeAddForm;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryReservationService;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryStocktakeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 盘点失败时的**真实回滚**（盘点波次）。
 *
 * <p><b>为什么这个类必须关掉测试事务</b>：W1–W5 的 IT 基类把整个用例包在一个事务里，
 * 于是 Service 上 {@code @Transactional(rollbackFor = Exception.class)} 只是**加入**这个事务；
 * 抛异常时 Spring 只把事务标记成 rollback-only，**不会**把已经写下的行撤掉。
 * 在这种环境下断言「失败后零残留」，看到的其实是「失败前写下的行还在」——
 * 断言会因为「检查发生在任何写入之前」而碰巧通过，但完全没有验证到原子性。
 * （与 {@link ScmInventoryRollbackIT} 同一取舍。）
 *
 * <p>本类用 {@code Propagation.NOT_SUPPORTED} 关掉外层事务，让每次 Service 调用
 * 自己开事务、自己提交或回滚。于是每个断言都是**独立事务里的已提交读**，
 * 一张盘点单要么整单生效，要么整单不生效。
 *
 * <p><b>代价</b>：造数会提交到开发库（与 {@code ScmInventoryRollbackIT} 同一取舍）。
 * 所有编码都带 {@link #prefix} 的随机后缀，不会与其它用例或既有数据冲突。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("盘点失败的真实回滚（PG IT，无外层事务）")
class ScmInventoryStocktakeRollbackIT extends ScmW6PgITBase {

    @Autowired
    private InventoryStocktakeService stocktakeService;

    @Autowired
    private InventoryReservationService reservations;

    @Override
    protected void evictMybatisCache() {
        // 无外层事务 → 每次调用都是新 session → 一级缓存天然为空
    }

    /**
     * 造一个已入库指定数量的 SKU。
     */
    private Long stocked(String suffix, String quantity) {
        Long skuId = newSkuOfType(suffix, "NON_STANDARD", "ON_SHELF");
        W6Fixture fixture = inboundFixture(suffix, skuId, quantity);
        confirmReceipt(fixture.receipt().getId(), quantity);
        return skuId;
    }

    /**
     * 两行盘点单（行序由服务端按 skuId 升序决定，因此先建的 SKU 先被处理）。
     */
    private InventoryStocktakeAddForm twoLineForm(Long warehouseId,
                                                  Long firstSku, String firstActual,
                                                  Long secondSku, String secondActual) {
        InventoryStocktakeAddForm form = new InventoryStocktakeAddForm();
        form.setWarehouseId(warehouseId);
        form.setItems(new ArrayList<>(List.of(
                item(firstSku, firstActual), item(secondSku, secondActual))));
        return form;
    }

    private static InventoryStocktakeAddForm.Item item(Long skuId, String actual) {
        InventoryStocktakeAddForm.Item row = new InventoryStocktakeAddForm.Item();
        row.setSkuId(skuId);
        row.setActualQuantity(new BigDecimal(actual));
        return row;
    }

    private List<Map<String, Object>> stocktakeMovements(Long wh, Long sku) {
        return movementsOf(wh, sku).stream()
                .filter(m -> String.valueOf(m.get("movement_type")).startsWith("STOCKTAKE_"))
                .toList();
    }

    @Test
    @DisplayName("前一行已写流水、后一行失败 → 整单回滚：余额零变化、流水零残留、单据仍是草稿")
    void failingLineRollsBackTheWholeStocktake() {
        Long wh = seedWarehouseId();

        // 先建的先处理（服务端按 skuId 升序），因此 first 是「已经成功写入」的那一行
        Long first = stocked("RB1A", "10.0000");
        Long second = stocked("RB1B", "10.0000");

        // second 已全部预留 10 → 实盘 0 会让 after = 0 < 已预留 10 → 41025
        // 来源标识取本次新建的 skuId：本类无外层事务，预留会提交到开发库，
        // 固定 id 会让同一个库上的第二次运行撞 uk 而假红（41016）。
        reservations.reserve(new ReserveInventoryFact(
                wh, second, "SALES_ORDER_ITEM", second, second,
                new BigDecimal("10.0000"), OffsetDateTime.now(), null));

        Long id = stocktakeService.create(
                twoLineForm(wh, first, "12.0000", second, "0.0000"));

        expectCode(() -> stocktakeService.confirm(id), 41025);

        // first 那一行本已在同一事务内写下了盘盈流水与余额调整，必须随整单一起消失
        assertThat(balanceRow(wh, first).getQuantity()).isEqualByComparingTo("10.0000");
        assertThat(stocktakeMovements(wh, first)).isEmpty();
        assertThat(balanceRow(wh, second).getQuantity()).isEqualByComparingTo("10.0000");
        assertThat(stocktakeMovements(wh, second)).isEmpty();

        // 单据仍是草稿、明细仍在 —— 用户修掉冲突行之后可以直接重试
        assertThat(jdbc.queryForObject(
                "SELECT status FROM inventory_stocktake WHERE id = ?", String.class, id))
                .isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM inventory_stocktake_item WHERE stocktake_id = ? AND deleted = FALSE",
                Integer.class, id)).isEqualTo(2);

        // 失败原因是那一行，不是别的东西坏了：把实盘改成与预留兼容后整单可以正常确认
        stocktakeService.update(id, twoLineForm(wh, first, "12.0000", second, "10.0000"));
        stocktakeService.confirm(id);

        assertThat(balanceRow(wh, first).getQuantity()).isEqualByComparingTo("12.0000");
        assertThat(balanceRow(wh, second).getQuantity()).isEqualByComparingTo("10.0000");
        assertThat(stocktakeMovements(wh, first)).hasSize(1);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM inventory_stocktake WHERE id = ?", String.class, id))
                .isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("盘亏推成负数 → 41024 且零残留（余额与流水都不动）")
    void negativeAdjustmentLeavesNoResidue() {
        Long wh = seedWarehouseId();
        Long sku = stocked("RB2A", "10.0000");

        InventoryStocktakeAddForm form = new InventoryStocktakeAddForm();
        form.setWarehouseId(wh);
        // 账面快照 10、实盘 0 → delta = −10；账面就是 10 → after = 0，正好不越界。
        // 为了构造「推成负数」，先把账面降到 3（出库 7），再盘亏到 0 → after = 3 − 10 = −7。
        form.setItems(new ArrayList<>(List.of(item(sku, "0.0000"))));
        Long id = stocktakeService.create(form);

        // 确认之前出库 7 → 确认瞬间账面 3，delta 仍以快照 10 为基线 = −10 → after = −7
        jdbc.update("UPDATE inventory_balance SET quantity = 3 WHERE warehouse_id = ? AND sku_id = ?",
                wh, sku);
        evictMybatisCache();

        expectCode(() -> stocktakeService.confirm(id), 41024);

        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("3.0000");
        assertThat(stocktakeMovements(wh, sku)).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM inventory_stocktake WHERE id = ?", String.class, id))
                .isEqualTo("DRAFT");
    }
}
