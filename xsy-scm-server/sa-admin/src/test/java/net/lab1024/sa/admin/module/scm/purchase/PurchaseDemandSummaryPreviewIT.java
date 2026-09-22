package net.lab1024.sa.admin.module.scm.purchase;

import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseDemandSummaryPreviewForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseDemandSummaryVO;
import net.lab1024.sa.admin.module.scm.purchase.service.PurchaseQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 订单汇总 / 库存缺口只读预览（Wave 2A §6A.12）。
 *
 * <p>验证的是「读侧口径」而非写入：预览必须与 {@code generate} 同源（已确认订单 + 实发量），
 * 并在 Q13 单位门禁、仓库隔离、无余额、四位定点这些边界上给出正确判定。
 *
 * <p>余额用 {@link #seedBalance} 直接插表：预览只<b>读</b>余额，不经收货入库链路，
 * 直插一个 {@code inventory_balance} 行是本用例要构造的「已知库存事实」夹具，不是绕过业务写入口
 * （写入口是 {@code InventoryCommandService}，本 Wave 完全不碰）。
 */
@DisplayName("采购缺口预览：只读聚合 / Q13 单位门禁 / 仓库隔离（PG IT）")
class PurchaseDemandSummaryPreviewIT extends ScmW5PgITBase {

    @Autowired
    private PurchaseQueryService queryService;

    private PurchaseDemandSummaryPreviewForm window(OffsetDateTime confirmedAt, Long warehouseId) {
        PurchaseDemandSummaryPreviewForm form = new PurchaseDemandSummaryPreviewForm();
        form.setStartAt(confirmedAt);
        form.setEndAt(confirmedAt.plusSeconds(2));
        form.setWarehouseId(warehouseId);
        form.setPageNum(1L);
        form.setPageSize(50L);
        return form;
    }

    /**
     * 直插一行库存余额（本用例的已知库存事实）。
     */
    private void seedBalance(Long warehouseId, Long skuId, String unit,
                             String quantity, String reserved) {
        jdbc.update("INSERT INTO inventory_balance (warehouse_id, sku_id, unit, quantity, reserved_quantity, "
                        + "avg_cost, version, deleted, created_at, updated_at, created_by, updated_by) "
                        + "VALUES (?, ?, ?, ?, ?, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'it', 'it')",
                warehouseId, skuId, unit, new BigDecimal(quantity), new BigDecimal(reserved));
        evictMybatisCache();
    }

    private PurchaseDemandSummaryVO rowFor(Long skuId, List<PurchaseDemandSummaryVO> rows) {
        return rows.stream().filter(r -> r.getSkuId().equals(skuId)).findFirst()
                .orElseThrow(() -> new AssertionError("预览结果缺少 SKU " + skuId));
    }

    @Test
    @DisplayName("库存足够：可用量 ≥ 订单量 → STOCK_ENOUGH，缺口 0.0000")
    void stockEnough() {
        Fixture fixture = fixture("S1", "5.0000", "3.0000");
        seedBalance(seedWarehouseId(), fixture.skuId(), DEFAULT_PURCHASE_UNIT, "10.0000", "2.0000");

        PurchaseDemandSummaryVO row = rowFor(fixture.skuId(),
                queryService.summaryPreview(window(fixture.confirmedAt(), seedWarehouseId())).getList());

        assertThat(row.getCalculationStatus()).isEqualTo("STOCK_ENOUGH");
        assertThat(row.getOrderDemandQuantity()).isEqualByComparingTo("3.0000");
        assertThat(row.getOnHandQuantity()).isEqualByComparingTo("10.0000");
        assertThat(row.getReservedQuantity()).isEqualByComparingTo("2.0000");
        assertThat(row.getAvailableQuantity()).isEqualByComparingTo("8.0000");
        assertThat(row.getShortageAgainstAvailable()).isEqualByComparingTo("0.0000");
        assertThat(row.getSourceOrderCount()).isEqualTo(1L);
        assertThat(row.getSourceLineCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("库存不足：按四位定点算出缺口 = 订单量 − 可用量")
    void shortage() {
        Fixture fixture = fixture("S2", "8.0000", "7.5000");
        seedBalance(seedWarehouseId(), fixture.skuId(), DEFAULT_PURCHASE_UNIT, "3.0000", "1.0000");

        PurchaseDemandSummaryVO row = rowFor(fixture.skuId(),
                queryService.summaryPreview(window(fixture.confirmedAt(), seedWarehouseId())).getList());

        // available = 3 - 1 = 2；shortage = 7.5 - 2 = 5.5，四位定点
        assertThat(row.getCalculationStatus()).isEqualTo("SHORTAGE");
        assertThat(row.getAvailableQuantity()).isEqualByComparingTo("2.0000");
        assertThat(row.getShortageAgainstAvailable()).isEqualByComparingTo("5.5000");
    }

    @Test
    @DisplayName("可用量为 0：ZERO_STOCK，缺口等于全部订单量")
    void zeroStock() {
        Fixture fixture = fixture("S3", "4.0000", "2.2000");
        // 现有 == 预留 → 可用 0
        seedBalance(seedWarehouseId(), fixture.skuId(), DEFAULT_PURCHASE_UNIT, "5.0000", "5.0000");

        PurchaseDemandSummaryVO row = rowFor(fixture.skuId(),
                queryService.summaryPreview(window(fixture.confirmedAt(), seedWarehouseId())).getList());

        assertThat(row.getCalculationStatus()).isEqualTo("ZERO_STOCK");
        assertThat(row.getAvailableQuantity()).isEqualByComparingTo("0.0000");
        assertThat(row.getShortageAgainstAvailable()).isEqualByComparingTo("2.2000");
    }

    @Test
    @DisplayName("Q13 单位门禁：余额记账单位 ≠ 需求单位 → UNIT_MISMATCH 且缺口为 null，绝不换算")
    void unitMismatchYieldsNullShortage() {
        Fixture fixture = fixture("S4", "9.0000", "4.0000");
        // 需求单位是 kg（基类销售单位），余额故意记成 g：单位不可直接相减
        seedBalance(seedWarehouseId(), fixture.skuId(), "g", "1000.0000", "0.0000");

        PurchaseDemandSummaryVO row = rowFor(fixture.skuId(),
                queryService.summaryPreview(window(fixture.confirmedAt(), seedWarehouseId())).getList());

        assertThat(row.getCalculationStatus()).isEqualTo("UNIT_MISMATCH");
        assertThat(row.getDemandUnit()).isEqualTo(DEFAULT_PURCHASE_UNIT);
        assertThat(row.getInventoryUnit()).isEqualTo("g");
        // 关键：不返回伪造缺口
        assertThat(row.getShortageAgainstAvailable()).isNull();
    }

    @Test
    @DisplayName("无库存余额行：NO_BALANCE，库存按 0 展示、缺口即全部订单量")
    void noBalance() {
        Fixture fixture = fixture("S5", "3.0000", "1.5000");

        PurchaseDemandSummaryVO row = rowFor(fixture.skuId(),
                queryService.summaryPreview(window(fixture.confirmedAt(), seedWarehouseId())).getList());

        assertThat(row.getCalculationStatus()).isEqualTo("NO_BALANCE");
        assertThat(row.getInventoryUnit()).isNull();
        assertThat(row.getOnHandQuantity()).isEqualByComparingTo("0.0000");
        assertThat(row.getAvailableQuantity()).isEqualByComparingTo("0.0000");
        assertThat(row.getShortageAgainstAvailable()).isEqualByComparingTo("1.5000");
    }

    @Test
    @DisplayName("多张已确认订单同 SKU：订单数与需求量正确聚合")
    void aggregatesMultipleOrdersOfSameSku() {
        Long customerId = newCustomer();
        Long skuId = newOnShelfSku("S6");
        // 同一 SKU 两张订单，实发量各 1.2 + 2.3
        Long orderA = confirmedSalesOrder(customerId, skuId, "5.0000", "1.2000");
        Long orderB = confirmedSalesOrder(customerId, skuId, "5.0000", "2.3000");
        OffsetDateTime earliest = salesOrderConfirmedAt(orderA);
        seedBalance(seedWarehouseId(), skuId, DEFAULT_PURCHASE_UNIT, "1.0000", "0.0000");

        PurchaseDemandSummaryVO row = rowFor(skuId,
                queryService.summaryPreview(window(earliest, seedWarehouseId())).getList());

        assertThat(row.getSourceOrderCount()).isEqualTo(2L);
        assertThat(row.getSourceLineCount()).isEqualTo(2L);
        assertThat(row.getOrderDemandQuantity()).isEqualByComparingTo("3.5000");
        // available 1；shortage 3.5 - 1 = 2.5
        assertThat(row.getShortageAgainstAvailable()).isEqualByComparingTo("2.5000");
    }

    @Test
    @DisplayName("仓库隔离：余额在别的仓库 → 目标仓库按 NO_BALANCE 处理，绝不跨仓混算")
    void doesNotMixWarehouses() {
        Fixture fixture = fixture("S7", "4.0000", "3.0000");
        Long otherWarehouse = newWarehouse("OTHER");
        seedBalance(otherWarehouse, fixture.skuId(), DEFAULT_PURCHASE_UNIT, "100.0000", "0.0000");

        PurchaseDemandSummaryVO row = rowFor(fixture.skuId(),
                queryService.summaryPreview(window(fixture.confirmedAt(), seedWarehouseId())).getList());

        // 目标仓库没有余额行，别的仓库的 100 完全不可见
        assertThat(row.getCalculationStatus()).isEqualTo("NO_BALANCE");
        assertThat(row.getOnHandQuantity()).isEqualByComparingTo("0.0000");
        assertThat(row.getShortageAgainstAvailable()).isEqualByComparingTo("3.0000");
    }

    @Test
    @DisplayName("只读：预览零库存写入、零采购需求写入，且重复调用结果一致")
    void isReadOnlyAndIdempotent() {
        Fixture fixture = fixture("S8", "6.0000", "2.0000");
        seedBalance(seedWarehouseId(), fixture.skuId(), DEFAULT_PURCHASE_UNIT, "1.0000", "0.0000");
        PurchaseDemandSummaryPreviewForm form = window(fixture.confirmedAt(), seedWarehouseId());

        List<PurchaseDemandSummaryVO> first = queryService.summaryPreview(form).getList();
        List<PurchaseDemandSummaryVO> second = queryService.summaryPreview(form).getList();
        assertThat(second).usingRecursiveComparison().isEqualTo(first);

        // 未生成任何采购需求
        assertThat(purchaseDemandDao.listActiveBySourceItemIds(List.of(fixture.salesOrderItemId()))).isEmpty();
        // 未产生任何库存流水
        Integer movements = jdbc.queryForObject(
                "SELECT count(*) FROM inventory_movement WHERE sku_id = ?", Integer.class, fixture.skuId());
        assertThat(movements).isZero();
    }
}
