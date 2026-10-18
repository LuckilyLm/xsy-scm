package com.xsy.scm.inventory;

import com.xsy.scm.common.ScmW6PgITBase;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.inventory.domain.InventoryOutboundFact;
import com.xsy.scm.inventory.domain.InventoryTransferFact;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptConfirmForm;
import com.xsy.scm.purchase.domain.vo.PurchaseReceiptItemVO;
import com.xsy.scm.purchase.domain.vo.PurchaseReceiptVO;
import com.xsy.scm.warehouse.domain.form.WarehouseAddForm;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 采购收货批次（一期 L1 批次）的实时路径。
 *
 * <p>
 * 断言的都是不变量而不是实现细节：批次余额之和等于总账余额、批次流水之和等于批次余额、
 * 出库按先进先出分摊、批次仓的其它库存动作被拒绝。夹具用本类独占的批次仓，
 * 避免与种子仓库里别人留下的余额行互相影响。
 */
@DisplayName("W9 采购收货批次（PG IT）")
class ScmInventoryLotIT extends ScmW6PgITBase {

    private Long lotWarehouseId;

    @BeforeEach
    void createLotWarehouse() {
        WarehouseAddForm form = new WarehouseAddForm();
        form.setName(prefix + "批次仓");
        form.setLotTrackingEnabled(true);
        lotWarehouseId = warehouseService.create(form);
    }

    @Override
    protected Long fixtureWarehouseId() {
        return lotWarehouseId;
    }

    private BigDecimal lotBalance(Long warehouseId, Long skuId) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(quantity), 0) FROM xsy_inventory_lot_balance "
                        + "WHERE warehouse_id = ? AND sku_id = ?",
                BigDecimal.class, warehouseId, skuId);
    }

    private BigDecimal totalBalance(Long warehouseId, Long skuId) {
        return jdbc.queryForObject(
                "SELECT quantity FROM xsy_inventory_balance WHERE warehouse_id = ? AND sku_id = ? AND deleted = FALSE",
                BigDecimal.class, warehouseId, skuId);
    }

    /** 用给定批次登记确认一张收货单；批次数量之和由调用方保证等于收货量。 */
    private PurchaseReceiptVO confirmWithLots(Long receiptId, String quantity, String... lotQuantities) {
        PurchaseReceiptVO current = reloadReceipt(receiptId);
        PurchaseReceiptItemVO line = current.getItems().getFirst();
        PurchaseReceiptConfirmForm.Item item = receiptLine(line.getId(), line.getVersion(), quantity);
        item.setLots(List.of(lotQuantities).stream().map(text -> {
            PurchaseReceiptConfirmForm.Lot lot = new PurchaseReceiptConfirmForm.Lot();
            lot.setSourceLotNo("SUP-" + text);
            lot.setQuantity(text);
            return lot;
        }).toList());
        return purchaseReceiptService.confirm(confirmForm(current.getId(), current.getVersion(), item),
                prefix + ":lot:" + UUID.randomUUID());
    }

    @Test
    @DisplayName("#1 一行拆两批：批次余额之和等于总账，批次流水与批次余额一致")
    void splitLotsKeepSubLedgerInSync() {
        Long skuId = newOnShelfSku("LOT1");
        W6Fixture fx = inboundFixture("LOT1", skuId, "10.0000");

        PurchaseReceiptVO confirmed = confirmWithLots(fx.receipt().getId(), "10.0000", "4.0000", "6.0000");
        assertThat(confirmed.getStatus()).isEqualTo("CONFIRMED");

        // 批次档案：两个批次，来源批号原样保存
        Integer lots = jdbc.queryForObject(
                "SELECT count(*) FROM xsy_trace_lot WHERE sku_id = ? AND source_lot_no LIKE 'SUP-%'",
                Integer.class, skuId);
        assertThat(lots).isEqualTo(2);

        // 不变量：批次余额之和 = 总账余额
        assertThat(lotBalance(lotWarehouseId, skuId)).isEqualByComparingTo(totalBalance(lotWarehouseId, skuId));
        assertThat(lotBalance(lotWarehouseId, skuId)).isEqualByComparingTo("10.0000");

        // 不变量：批次流水之和 = 批次余额
        BigDecimal movementSum = jdbc.queryForObject(
                "SELECT COALESCE(SUM(quantity), 0) FROM xsy_inventory_lot_movement "
                        + "WHERE warehouse_id = ? AND sku_id = ?",
                BigDecimal.class, lotWarehouseId, skuId);
        assertThat(movementSum).isEqualByComparingTo("10.0000");

        // 收货行批次事实的数量之和等于本次收货量
        BigDecimal declared = jdbc.queryForObject(
                "SELECT COALESCE(SUM(l.quantity), 0) FROM xsy_purchase_receipt_item_lot l "
                        + "JOIN xsy_purchase_receipt_item i ON i.id = l.purchase_receipt_item_id "
                        + "WHERE i.purchase_receipt_id = ?",
                BigDecimal.class, fx.receipt().getId());
        assertThat(declared).isEqualByComparingTo("10.0000");
    }

    @Test
    @DisplayName("#2 出库按先进先出分摊：先入批次先出，子账之和等于总账")
    void outboundAllocatesFifoLots() {
        Long skuId = newOnShelfSku("LOT2");
        W6Fixture fx = inboundFixture("LOT2", skuId, "10.0000");
        confirmWithLots(fx.receipt().getId(), "10.0000", "4.0000", "6.0000");

        inventoryCommandService.postSalesOutbound(new InventoryOutboundFact(lotWarehouseId, skuId, 9_900_001L,
                9_900_002L, new BigDecimal("5.0000"), new BigDecimal("6.2000"), OffsetDateTime.now(), "W9 IT"));

        // 先进先出：第一批 4 全部出掉，第二批出 1，剩 5
        List<BigDecimal> remaining = jdbc.queryForList(
                "SELECT quantity FROM xsy_inventory_lot_balance WHERE warehouse_id = ? AND sku_id = ? "
                        + "ORDER BY lot_id",
                BigDecimal.class, lotWarehouseId, skuId);
        assertThat(remaining).hasSize(2);
        assertThat(remaining.get(0)).isEqualByComparingTo("0.0000");
        assertThat(remaining.get(1)).isEqualByComparingTo("5.0000");

        assertThat(lotBalance(lotWarehouseId, skuId)).isEqualByComparingTo(totalBalance(lotWarehouseId, skuId));

        // 子账流水有符号：入库 +10、出库 -5
        BigDecimal movementSum = jdbc.queryForObject(
                "SELECT COALESCE(SUM(quantity), 0) FROM xsy_inventory_lot_movement "
                        + "WHERE warehouse_id = ? AND sku_id = ?",
                BigDecimal.class, lotWarehouseId, skuId);
        assertThat(movementSum).isEqualByComparingTo("5.0000");
    }

    @Test
    @DisplayName("#3 批次仓拒绝调拨（一期只支持采购入库与销售出库）")
    void lotWarehouseRejectsTransfer() {
        Long skuId = newOnShelfSku("LOT3");
        W6Fixture fx = inboundFixture("LOT3", skuId, "10.0000");
        confirmWithLots(fx.receipt().getId(), "10.0000", "10.0000");

        assertThatThrownBy(() -> inventoryCommandService.postTransferOut(new InventoryTransferFact(lotWarehouseId,
                skuId, 9_900_101L, 9_900_102L, new BigDecimal("1.0000"), DEFAULT_PURCHASE_UNIT,
                OffsetDateTime.now(), "W9 IT")))
                .isInstanceOf(ScmBusinessException.class)
                .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode()).isEqualTo(41070));
    }

    /**
     * 批次仓不登记批次 → 40095。
     *
     * <p>
     * 与下一条拆成两个用例：异常会把测试事务标记为 rollback-only，
     * 同一个用例里再发第二次命令会得到 40921 而不是被测的行为。
     */
    @Test
    @DisplayName("#4 批次仓不登记批次时拒绝确认（40095）")
    void lotWarehouseRequiresLots() {
        Long skuId = newOnShelfSku("LOT4");
        W6Fixture fx = inboundFixture("LOT4", skuId, "10.0000");

        PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
        PurchaseReceiptItemVO line = current.getItems().getFirst();
        PurchaseReceiptConfirmForm.Item withoutLots = receiptLine(line.getId(), line.getVersion(), "10.0000");
        assertThatThrownBy(() -> purchaseReceiptService.confirm(
                confirmForm(current.getId(), current.getVersion(), withoutLots),
                prefix + ":lot:none:" + UUID.randomUUID()))
                .isInstanceOf(ScmBusinessException.class)
                .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode()).isEqualTo(40095));
    }

    @Test
    @DisplayName("#5 批次数量之和与收货量不一致时拒绝确认（40096）")
    void lotQuantitiesMustMatchReceiptQuantity() {
        Long skuId = newOnShelfSku("LOT5");
        W6Fixture fx = inboundFixture("LOT5", skuId, "10.0000");

        PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
        PurchaseReceiptItemVO line = current.getItems().getFirst();
        PurchaseReceiptConfirmForm.Item mismatched = receiptLine(line.getId(), line.getVersion(), "10.0000");
        PurchaseReceiptConfirmForm.Lot lot = new PurchaseReceiptConfirmForm.Lot();
        lot.setQuantity("9.0000");
        mismatched.setLots(List.of(lot));
        assertThatThrownBy(() -> purchaseReceiptService.confirm(
                confirmForm(current.getId(), current.getVersion(), mismatched),
                prefix + ":lot:mismatch:" + UUID.randomUUID()))
                .isInstanceOf(ScmBusinessException.class)
                .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode()).isEqualTo(40096));
    }
}
