package net.lab1024.sa.admin.module.scm.purchase;

import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.inventory.domain.ReserveInventoryFact;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryReservationService;
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
 * <p><b>预留口径</b>：{@code inventory_balance.reserved_quantity} 是全仓总量，其中包含本批订单
 * 自己占用的预留。预览必须把「本批自身预留」从占用里排除后再比对，否则已备好货的订单会被
 * 算成等额采购缺口（见 {@link #ownReservationDoesNotInflateGap}）。
 *
 * <p>余额用 {@link #seedBalance} 直接插表：预览只<b>读</b>余额，不经收货入库链路，
 * 直插一个 {@code inventory_balance} 行是本用例要构造的「已知库存事实」夹具，不是绕过业务写入口
 * （写入口是 {@code InventoryCommandService}，本 Wave 完全不碰）。预留则一律走
 * {@code InventoryReservationService.reserve} 真实写入口，保证 {@code reserved_quantity}
 * 与预留行由同一事务维护，夹具不会造出库里不可能出现的状态。
 */
@DisplayName("采购缺口预览：只读聚合 / 预留归属 / Q13 单位门禁 / 仓库隔离（PG IT）")
class PurchaseDemandSummaryPreviewIT extends ScmW5PgITBase {

    @Autowired
    private PurchaseQueryService queryService;

    @Autowired
    private InventoryReservationService reservationService;

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

    /**
     * 为一已确认订单在其来源行上预留库存（走真实写入口，同步维护余额 {@code reserved_quantity}）。
     *
     * <p>{@code occurredAt} 取该订单的确认时刻，与 {@code reserveBySalesOrder} 的口径一致。
     */
    private void reserveForOrder(Long warehouseId, Long skuId, Long salesOrderId, String quantity) {
        reservationService.reserve(new ReserveInventoryFact(
                warehouseId, skuId, "SALES_ORDER_ITEM",
                salesOrderId, confirmedSalesOrderItemId(salesOrderId),
                new BigDecimal(quantity), salesOrderConfirmedAt(salesOrderId), "2:1"));
        evictMybatisCache();
    }

    private PurchaseDemandSummaryVO rowFor(Long skuId, List<PurchaseDemandSummaryVO> rows) {
        return rows.stream().filter(r -> r.getSkuId().equals(skuId)).findFirst()
                .orElseThrow(() -> new AssertionError("预览结果缺少 SKU " + skuId));
    }

    private List<PurchaseDemandSummaryVO> preview(Long warehouseId, OffsetDateTime confirmedAt) {
        return queryService.summaryPreview(window(confirmedAt, warehouseId)).getList();
    }

    @Test
    @DisplayName("库存足够且无任何预留：本批可用 = 全仓净可用，差额 0.0000")
    void stockEnough() {
        Fixture fixture = fixture("S1", "5.0000", "3.0000");
        seedBalance(seedWarehouseId(), fixture.skuId(), DEFAULT_PURCHASE_UNIT, "10.0000", "0.0000");

        PurchaseDemandSummaryVO row = rowFor(fixture.skuId(), preview(seedWarehouseId(), fixture.confirmedAt()));

        assertThat(row.getCalculationStatus()).isEqualTo("STOCK_ENOUGH");
        assertThat(row.getOrderDemandQuantity()).isEqualByComparingTo("3.0000");
        assertThat(row.getOnHandQuantity()).isEqualByComparingTo("10.0000");
        assertThat(row.getReservedQuantity()).isEqualByComparingTo("0.0000");
        assertThat(row.getSelectedOrderReservedQuantity()).isEqualByComparingTo("0.0000");
        assertThat(row.getOtherReservedQuantity()).isEqualByComparingTo("0.0000");
        assertThat(row.getAvailableQuantity()).isEqualByComparingTo("10.0000");
        assertThat(row.getStockAvailableForSelectedOrders()).isEqualByComparingTo("10.0000");
        assertThat(row.getStockComparisonGap()).isEqualByComparingTo("0.0000");
        assertThat(row.getSourceOrderCount()).isEqualTo(1L);
        assertThat(row.getSourceLineCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("库存不足且占用属于其他订单：其他预留照扣，差额按本批可用算")
    void shortage() {
        Long customerId = newCustomer();
        Long skuId = newOnShelfSku("S2");
        seedBalance(seedWarehouseId(), skuId, DEFAULT_PURCHASE_UNIT, "3.0000", "0.0000");
        // 先建的订单：确认时刻早于本批窗口，它的 1.0 预留属于「其他业务占用」
        Long otherOrder = confirmedSalesOrder(customerId, skuId, "8.0000", "1.0000");
        reserveForOrder(seedWarehouseId(), skuId, otherOrder, "1.0000");
        // 本批订单需求 7.5，自身没有预留
        Long batchOrder = confirmedSalesOrder(customerId, skuId, "8.0000", "7.5000");
        OffsetDateTime confirmedAt = salesOrderConfirmedAt(batchOrder);
        assertThat(salesOrderConfirmedAt(otherOrder)).isBefore(confirmedAt);

        PurchaseDemandSummaryVO row = rowFor(skuId, preview(seedWarehouseId(), confirmedAt));

        assertThat(row.getCalculationStatus()).isEqualTo("SHORTAGE");
        assertThat(row.getOrderDemandQuantity()).isEqualByComparingTo("7.5000");
        assertThat(row.getSourceOrderCount()).isEqualTo(1L);
        assertThat(row.getReservedQuantity()).isEqualByComparingTo("1.0000");
        assertThat(row.getSelectedOrderReservedQuantity()).isEqualByComparingTo("0.0000");
        assertThat(row.getOtherReservedQuantity()).isEqualByComparingTo("1.0000");
        // available = 3 - 1 = 2；本批可用同样是 2（没有自己的预留可还），差额 7.5 - 2 = 5.5
        assertThat(row.getAvailableQuantity()).isEqualByComparingTo("2.0000");
        assertThat(row.getStockAvailableForSelectedOrders()).isEqualByComparingTo("2.0000");
        assertThat(row.getStockComparisonGap()).isEqualByComparingTo("5.5000");
    }

    @Test
    @DisplayName("本批全额预留（固定反例）：需求 100 / 现货 100 / 本批自身预留 100 → 差额 0，绝不是 100")
    void ownReservationDoesNotInflateGap() {
        Long customerId = newCustomer();
        Long skuId = newOnShelfSku("S2A");
        seedBalance(seedWarehouseId(), skuId, DEFAULT_PURCHASE_UNIT, "100.0000", "0.0000");
        Long orderId = confirmedSalesOrder(customerId, skuId, "100.0000", "100.0000");
        OffsetDateTime confirmedAt = salesOrderConfirmedAt(orderId);
        reserveForOrder(seedWarehouseId(), skuId, orderId, "100.0000");

        PurchaseDemandSummaryVO row = rowFor(skuId, preview(seedWarehouseId(), confirmedAt));

        // 全仓净可用被自己的预留打到 0，但那是本批订单已经占好的货，不构成采购缺口
        assertThat(row.getAvailableQuantity()).isEqualByComparingTo("0.0000");
        assertThat(row.getSelectedOrderReservedQuantity()).isEqualByComparingTo("100.0000");
        assertThat(row.getOtherReservedQuantity()).isEqualByComparingTo("0.0000");
        assertThat(row.getStockAvailableForSelectedOrders()).isEqualByComparingTo("100.0000");
        assertThat(row.getStockComparisonGap()).isEqualByComparingTo("0.0000");
        assertThat(row.getCalculationStatus()).isEqualTo("STOCK_ENOUGH");
    }

    @Test
    @DisplayName("本批部分预留：只有未覆盖的那部分进入差额")
    void partialOwnReservation() {
        Long customerId = newCustomer();
        Long skuId = newOnShelfSku("S2B");
        seedBalance(seedWarehouseId(), skuId, DEFAULT_PURCHASE_UNIT, "10.0000", "0.0000");
        Long orderId = confirmedSalesOrder(customerId, skuId, "8.0000", "8.0000");
        OffsetDateTime confirmedAt = salesOrderConfirmedAt(orderId);
        reserveForOrder(seedWarehouseId(), skuId, orderId, "3.0000");

        PurchaseDemandSummaryVO row = rowFor(skuId, preview(seedWarehouseId(), confirmedAt));

        assertThat(row.getReservedQuantity()).isEqualByComparingTo("3.0000");
        assertThat(row.getSelectedOrderReservedQuantity()).isEqualByComparingTo("3.0000");
        assertThat(row.getAvailableQuantity()).isEqualByComparingTo("7.0000");
        assertThat(row.getStockAvailableForSelectedOrders()).isEqualByComparingTo("10.0000");
        // 需求 8 ≤ 本批可用 10 → 无差额
        assertThat(row.getStockComparisonGap()).isEqualByComparingTo("0.0000");
        assertThat(row.getCalculationStatus()).isEqualTo("STOCK_ENOUGH");
    }

    @Test
    @DisplayName("本批 + 其他业务预留同时存在：其他占用照扣，本批预留照还")
    void ownAndOtherReservationTogether() {
        Long customerId = newCustomer();
        Long skuId = newOnShelfSku("S2C");
        seedBalance(seedWarehouseId(), skuId, DEFAULT_PURCHASE_UNIT, "20.0000", "0.0000");
        // 窗口外的旧订单：它的 6.0 预留属于「其他业务占用」
        Long otherOrder = confirmedSalesOrder(customerId, skuId, "6.0000", "6.0000");
        reserveForOrder(seedWarehouseId(), skuId, otherOrder, "6.0000");
        // 窗口内的本批订单：它自己的 4.0 预留不能算成占用
        Long batchOrder = confirmedSalesOrder(customerId, skuId, "9.0000", "9.0000");
        OffsetDateTime confirmedAt = salesOrderConfirmedAt(batchOrder);
        reserveForOrder(seedWarehouseId(), skuId, batchOrder, "4.0000");

        PurchaseDemandSummaryVO row = rowFor(skuId, preview(seedWarehouseId(), confirmedAt));

        assertThat(row.getReservedQuantity()).isEqualByComparingTo("10.0000");
        assertThat(row.getSelectedOrderReservedQuantity()).isEqualByComparingTo("4.0000");
        assertThat(row.getOtherReservedQuantity()).isEqualByComparingTo("6.0000");
        assertThat(row.getAvailableQuantity()).isEqualByComparingTo("10.0000");
        assertThat(row.getStockAvailableForSelectedOrders()).isEqualByComparingTo("14.0000");
        assertThat(row.getStockComparisonGap()).isEqualByComparingTo("0.0000");
        // 只统计窗口内那一张订单的需求
        assertThat(row.getOrderDemandQuantity()).isEqualByComparingTo("9.0000");
        assertThat(row.getSourceOrderCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("其他业务预留吃掉可用量：本批无预留时仍按全仓净可用判定 ZERO_STOCK")
    void zeroStock() {
        Fixture fixture = fixture("S3", "4.0000", "2.2000");
        // 现有 == 预留 → 本批可用 0
        seedBalance(seedWarehouseId(), fixture.skuId(), DEFAULT_PURCHASE_UNIT, "5.0000", "5.0000");

        PurchaseDemandSummaryVO row = rowFor(fixture.skuId(), preview(seedWarehouseId(), fixture.confirmedAt()));

        assertThat(row.getCalculationStatus()).isEqualTo("ZERO_STOCK");
        assertThat(row.getSelectedOrderReservedQuantity()).isEqualByComparingTo("0.0000");
        assertThat(row.getOtherReservedQuantity()).isEqualByComparingTo("5.0000");
        assertThat(row.getAvailableQuantity()).isEqualByComparingTo("0.0000");
        assertThat(row.getStockAvailableForSelectedOrders()).isEqualByComparingTo("0.0000");
        assertThat(row.getStockComparisonGap()).isEqualByComparingTo("2.2000");
    }

    @Test
    @DisplayName("Q13 单位门禁：余额记账单位 ≠ 需求单位 → UNIT_MISMATCH 且差额为 null，绝不换算")
    void unitMismatchYieldsNullGap() {
        Fixture fixture = fixture("S4", "9.0000", "4.0000");
        // 需求单位是 kg（基类销售单位），余额故意记成 g：单位不可直接相减
        seedBalance(seedWarehouseId(), fixture.skuId(), "g", "1000.0000", "0.0000");

        PurchaseDemandSummaryVO row = rowFor(fixture.skuId(), preview(seedWarehouseId(), fixture.confirmedAt()));

        assertThat(row.getCalculationStatus()).isEqualTo("UNIT_MISMATCH");
        assertThat(row.getDemandUnit()).isEqualTo(DEFAULT_PURCHASE_UNIT);
        assertThat(row.getInventoryUnit()).isEqualTo("g");
        // 关键：不返回伪造差额
        assertThat(row.getStockComparisonGap()).isNull();
    }

    @Test
    @DisplayName("无库存余额行：NO_BALANCE，库存按 0 展示、差额即全部订单量")
    void noBalance() {
        Fixture fixture = fixture("S5", "3.0000", "1.5000");

        PurchaseDemandSummaryVO row = rowFor(fixture.skuId(), preview(seedWarehouseId(), fixture.confirmedAt()));

        assertThat(row.getCalculationStatus()).isEqualTo("NO_BALANCE");
        assertThat(row.getInventoryUnit()).isNull();
        assertThat(row.getOnHandQuantity()).isEqualByComparingTo("0.0000");
        assertThat(row.getSelectedOrderReservedQuantity()).isEqualByComparingTo("0.0000");
        assertThat(row.getStockAvailableForSelectedOrders()).isEqualByComparingTo("0.0000");
        assertThat(row.getStockComparisonGap()).isEqualByComparingTo("1.5000");
    }

    @Test
    @DisplayName("多张已确认订单同 SKU：订单数、需求量与两笔本批预留一起聚合")
    void aggregatesMultipleOrdersOfSameSku() {
        Long customerId = newCustomer();
        Long skuId = newOnShelfSku("S6");
        seedBalance(seedWarehouseId(), skuId, DEFAULT_PURCHASE_UNIT, "1.0000", "0.0000");
        // 同一 SKU 两张订单，实发量各 1.2 + 2.3；两张都属于本批
        Long orderA = confirmedSalesOrder(customerId, skuId, "5.0000", "1.2000");
        Long orderB = confirmedSalesOrder(customerId, skuId, "5.0000", "2.3000");
        OffsetDateTime earliest = salesOrderConfirmedAt(orderA);
        reserveForOrder(seedWarehouseId(), skuId, orderA, "1.0000");

        PurchaseDemandSummaryVO row = rowFor(skuId, preview(seedWarehouseId(), earliest));

        assertThat(row.getSourceOrderCount()).isEqualTo(2L);
        assertThat(row.getSourceLineCount()).isEqualTo(2L);
        assertThat(row.getOrderDemandQuantity()).isEqualByComparingTo("3.5000");
        // 本批可用 = 1.0（现货）- 0（其他占用）= 1.0；A 的 1.0 预留还回来 → 差额 3.5 - 1.0 = 2.5
        assertThat(row.getSelectedOrderReservedQuantity()).isEqualByComparingTo("1.0000");
        assertThat(row.getStockComparisonGap()).isEqualByComparingTo("2.5000");
    }

    @Test
    @DisplayName("仓库隔离：余额与预留都在别的仓库 → 目标仓库按 NO_BALANCE 处理，绝不跨仓混算")
    void doesNotMixWarehouses() {
        Fixture fixture = fixture("S7", "4.0000", "3.0000");
        Long otherWarehouse = newWarehouse("OTHER");
        seedBalance(otherWarehouse, fixture.skuId(), DEFAULT_PURCHASE_UNIT, "100.0000", "0.0000");
        reserveForOrder(otherWarehouse, fixture.skuId(), fixture.salesOrderId(), "3.0000");

        PurchaseDemandSummaryVO row = rowFor(fixture.skuId(), preview(seedWarehouseId(), fixture.confirmedAt()));

        // 目标仓库没有余额行，别的仓库的 100 与其预留完全不可见
        assertThat(row.getCalculationStatus()).isEqualTo("NO_BALANCE");
        assertThat(row.getOnHandQuantity()).isEqualByComparingTo("0.0000");
        assertThat(row.getReservedQuantity()).isEqualByComparingTo("0.0000");
        assertThat(row.getSelectedOrderReservedQuantity()).isEqualByComparingTo("0.0000");
        assertThat(row.getStockComparisonGap()).isEqualByComparingTo("3.0000");
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
        // 未新增任何预留行
        Integer reservations = jdbc.queryForObject(
                "SELECT count(*) FROM inventory_reservation WHERE sku_id = ?", Integer.class, fixture.skuId());
        assertThat(reservations).isZero();
    }
}
