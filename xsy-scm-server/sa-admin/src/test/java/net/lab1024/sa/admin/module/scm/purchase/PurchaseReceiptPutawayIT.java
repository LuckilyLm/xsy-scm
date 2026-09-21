package net.lab1024.sa.admin.module.scm.purchase;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.purchase.constant.ScmReceiptModeEnum;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptCreateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptPutawayForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 入库方式 / 仓库确认入库（B1，HD-B1-01/02/03，PG IT）。
 *
 * <p>核心不变量：{@code purchase_receipt.status=CONFIRMED} 与「库存已入账」解耦。
 * <ul>
 *   <li>DIRECT：confirm 同事务完成 putaway（COMPLETED）+ PURCHASE_IN；</li>
 *   <li>WAREHOUSE_CONFIRM：confirm 只产生商业事实（PENDING、无流水、余额不变），
 *       putaway 才写 PURCHASE_IN，且 occurred_at = putaway_at；</li>
 *   <li>重复 / 越权 putaway：41008 拒绝，绝不重复入库（DB 唯一索引兜底）。</li>
 * </ul>
 */
@DisplayName("入库方式与仓库确认入库（PG IT）")
class PurchaseReceiptPutawayIT extends ScmW6PgITBase {

    /**
     * 造一张「无需求来源 + WAREHOUSE_CONFIRM」的收货单（可自定义采购单位），返回草稿收货单。
     */
    private PurchaseReceiptVO warehouseConfirmReceipt(String suffix, Long skuId, String quantity,
                                                      String purchaseUnit, Long orderId) {
        PurchaseReceiptCreateForm form = new PurchaseReceiptCreateForm();
        form.setPurchaseOrderId(orderId);
        form.setReceiptMode(ScmReceiptModeEnum.WAREHOUSE_CONFIRM.name());
        form.setRemark("B1 IT 收货单");
        return purchaseReceiptService.create(form, prefix + ":wc:" + suffix + ":" + orderId);
    }

    /**
     * 一张「无需求来源 + 可自定义采购单位」的已提交采购单。
     */
    private PurchaseOrderVO freeSubmittedOrder(String suffix, Long skuId, String quantity,
                                               String purchaseUnit) {
        Long supplierId = newSupplier(suffix);
        linkSupplierSku(supplierId, skuId, purchaseUnit);
        PurchaseOrderVO order = createDraftOrder(suffix, supplierId, skuId, quantity, "6.2000");
        return submitOrder(order.getId());
    }

    private PurchaseReceiptVO putawayReceipt(Long receiptId) {
        PurchaseReceiptVO current = reloadReceipt(receiptId);
        PurchaseReceiptPutawayForm form = new PurchaseReceiptPutawayForm();
        form.setId(receiptId);
        form.setVersion(current.getVersion());
        return purchaseReceiptService.putaway(
                form, prefix + ":putaway:" + receiptId + ":" + UUID.randomUUID());
    }

    // ------------------------------------------------------------------
    // 1. DIRECT：confirm 同事务完成 putaway + PURCHASE_IN
    // ------------------------------------------------------------------

    @Test
    @DisplayName("DIRECT：confirm 后 receipt=CONFIRMED 且 putaway=COMPLETED，仅一条 PURCHASE_IN")
    void directConfirmCompletesPutawayInTheSameTransaction() {
        Long warehouseId = seedWarehouseId();
        Long skuId = newOnShelfSku("PD1");
        W6Fixture fx = inboundFixture("PD1", skuId, "8.0000");
        PurchaseReceiptVO confirmed = confirmReceipt(fx.receipt().getId(), "8.0000");

        assertThat(confirmed.getStatus()).isEqualTo("CONFIRMED");
        assertThat(confirmed.getReceiptMode()).isEqualTo("DIRECT");
        assertThat(confirmed.getPutawayStatus()).isEqualTo("COMPLETED");
        assertThat(confirmed.getPutawayAt()).isNotNull();
        assertThat(confirmed.getPutawayBy()).isEqualTo(confirmed.getOperator());

        assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("8.0000");
        assertThat(movementCount(warehouseId, skuId)).isEqualTo(1);
        assertThat(movementsOfReceiptItem(fx.receiptItemId())).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // 2. WAREHOUSE_CONFIRM：confirm 只产生商业事实，不写库存
    // ------------------------------------------------------------------

    @Test
    @DisplayName("WAREHOUSE_CONFIRM：confirm 后 PENDING、无流水、余额不变")
    void warehouseConfirmDoesNotWriteInventory() {
        Long warehouseId = seedWarehouseId();
        Long skuId = newOnShelfSku("PD2");
        PurchaseOrderVO order = freeSubmittedOrder("PD2", skuId, "8.0000", DEFAULT_PURCHASE_UNIT);
        PurchaseReceiptVO receipt = warehouseConfirmReceipt("PD2", skuId, "8.0000", DEFAULT_PURCHASE_UNIT, order.getId());

        PurchaseReceiptVO confirmed = confirmReceipt(receipt.getId(), "8.0000");
        assertThat(confirmed.getStatus()).isEqualTo("CONFIRMED");
        assertThat(confirmed.getReceiptMode()).isEqualTo("WAREHOUSE_CONFIRM");
        assertThat(confirmed.getPutawayStatus()).isEqualTo("PENDING");
        assertThat(confirmed.getPutawayAt()).isNull();
        assertThat(confirmed.getPutawayBy()).isNull();

        assertThat(balanceRow(warehouseId, skuId)).isNull();
        assertThat(movementCount(warehouseId, skuId)).isEqualTo(0);
    }

    // ------------------------------------------------------------------
    // 3. putaway：COMPLETED + PURCHASE_IN，occurred_at = putaway_at
    // ------------------------------------------------------------------

    @Test
    @DisplayName("putaway：COMPLETED、余额入库、流水 occurred_at = putaway_at（非 confirmed_at）")
    void putawayWritesInventoryWithPhysicalTime() {
        Long warehouseId = seedWarehouseId();
        Long skuId = newOnShelfSku("PD3");
        PurchaseOrderVO order = freeSubmittedOrder("PD3", skuId, "8.0000", DEFAULT_PURCHASE_UNIT);
        PurchaseReceiptVO receipt = warehouseConfirmReceipt("PD3", skuId, "8.0000", DEFAULT_PURCHASE_UNIT, order.getId());
        confirmReceipt(receipt.getId(), "8.0000");

        PurchaseReceiptVO putaway = putawayReceipt(receipt.getId());
        assertThat(putaway.getPutawayStatus()).isEqualTo("COMPLETED");
        assertThat(putaway.getPutawayAt()).isNotNull();
        assertThat(putaway.getPutawayBy()).isNotNull();

        assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("8.0000");
        assertThat(movementCount(warehouseId, skuId)).isEqualTo(1);

        Map<String, Object> mv = movementsOf(warehouseId, skuId).getFirst();
        OffsetDateTime occurredAt = timestampOf(mv.get("occurred_at"));
        OffsetDateTime putawayAt = jdbc.queryForObject(
                "SELECT putaway_at FROM purchase_receipt WHERE id = ?", OffsetDateTime.class, receipt.getId());
        OffsetDateTime confirmedAt = receiptConfirmedAt(receipt.getId());
        assertThat(occurredAt.toInstant()).isEqualTo(putawayAt.toInstant());
        assertThat(mv.get("operator")).isEqualTo(putaway.getPutawayBy());
        // 与 confirm 是两次独立动作：putaway 时刻不早于 confirm 时刻（HD-B1-03 语义）。
        assertThat(putawayAt.toInstant()).isAfterOrEqualTo(confirmedAt.toInstant());
    }

    // ------------------------------------------------------------------
    // 4. 重复 putaway：41008，不重复入库
    // ------------------------------------------------------------------

    @Test
    @DisplayName("重复 putaway：第二次 41008，余额与流水不重复")
    void secondPutawayIsRejectedAndDoesNotDuplicateInbound() {
        Long warehouseId = seedWarehouseId();
        Long skuId = newOnShelfSku("PD4");
        PurchaseOrderVO order = freeSubmittedOrder("PD4", skuId, "8.0000", DEFAULT_PURCHASE_UNIT);
        PurchaseReceiptVO receipt = warehouseConfirmReceipt("PD4", skuId, "8.0000", DEFAULT_PURCHASE_UNIT, order.getId());
        confirmReceipt(receipt.getId(), "8.0000");
        putawayReceipt(receipt.getId());

        expectCode(() -> putawayReceipt(receipt.getId()), 41008);

        assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("8.0000");
        assertThat(movementCount(warehouseId, skuId)).isEqualTo(1);
        assertThat(movementsOfReceiptItem(receipt.getItems().getFirst().getId())).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // 5. 历史 CONFIRMED 收货 → DIRECT / COMPLETED（B1 迁移的事实映射）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("历史 DIRECT 收货确认后 = DIRECT / COMPLETED，且不额外产生流水")
    void historicalConfirmedReceiptMapsToDirectCompleted() {
        Long warehouseId = seedWarehouseId();
        Long skuId = newOnShelfSku("PD6");
        W6Fixture fx = inboundFixture("PD6", skuId, "8.0000");
        confirmReceipt(fx.receipt().getId(), "8.0000");

        PurchaseReceiptVO receipt = reloadReceipt(fx.receipt().getId());
        assertThat(receipt.getReceiptMode()).isEqualTo("DIRECT");
        assertThat(receipt.getPutawayStatus()).isEqualTo("COMPLETED");
        assertThat(receipt.getPutawayAt()).isEqualTo(receipt.getConfirmedAt());
        assertThat(receipt.getPutawayBy()).isEqualTo(receipt.getOperator());

        // 迁移与实时路径共享同一条源身份唯一索引 → 不会重复产生 PURCHASE_IN
        assertThat(movementCount(warehouseId, skuId)).isEqualTo(1);
        assertThat(movementsOfReceiptItem(fx.receiptItemId())).isEqualTo(1);
    }
}
