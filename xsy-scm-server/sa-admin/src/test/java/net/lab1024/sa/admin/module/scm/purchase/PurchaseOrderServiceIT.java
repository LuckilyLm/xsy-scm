package net.lab1024.sa.admin.module.scm.purchase;

import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandAllocationEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderAddForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderCancelForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderDeleteForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderShortCloseForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderUpdateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderVersionForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOperationLogVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderAllocationVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderItemVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 采购单生命周期（W5 Target Design §11.2，10 例）。
 *
 * <p>覆盖 `create / update / submit / cancel / delete / short-close` 的**正例 + 状态机边界**，
 * 以及每次写入对**需求侧**的连带影响（`allocated_quantity` 与 `status` 必须跟着动）。
 *
 * <p><b>为什么需求侧断言和采购单断言同等重要</b>：§4.4 的三态（PENDING / PARTIALLY_ALLOCATED /
 * ALLOCATED）是「需求能被重新分配」的前提。采购单侧的 CRUD 只要漏掉一次
 * {@code recomputeDemands}，需求就会永久卡在 ALLOCATED —— 而采购单自己的字段看起来完全正常，
 * 所以这类回归只有在这里才拦得住。
 *
 * <p><b>不 mock 需求来源</b>：需求由 W4 已验收的订单（create → submit → actualQuantity → confirm）
 * 经 {@code PurchaseDemandService.generate} 真实汇总而来，见 {@link ScmW5PgITBase}。
 */
@DisplayName("采购单生命周期：create / update / submit / cancel / delete（PG IT）")
class PurchaseOrderServiceIT extends ScmW5PgITBase {

    // ------------------------------------------------------------------
    // 1. create
    // ------------------------------------------------------------------

    @Test
    @DisplayName("create：单号 / 快照 / 金额 / 分配 / 需求侧一次写全")
    void createPersistsOrderWithNumberSnapshotsAndTotalAmount() {
        Long skuId = newOnShelfSku("PO1");
        Long supplierId = newPurchasableSupplier("PO1", skuId);
        Long customerId = newCustomer();
        Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
        PurchaseDemandEntity demand = generateDemandFor(supplierId, salesOrder);

        PurchaseOrderVO order = createDraftOrder("PO1", supplierId, skuId, "3.0000", "6.2000",
                allocation(demand, "3.0000"));

        // 单号 = PO + yyyyMMdd + ≥6 位序号（PG sequence 全局递增，不按日 reset）
        assertThat(order.getOrderNo()).matches("^PO\\d{14,}$");
        assertThat(order.getStatus()).isEqualTo("DRAFT");

        // 供应商 / 仓库快照在创建时固化（§7.3：历史永不回读主数据）
        assertThat(order.getSupplierId()).isEqualTo(supplierId);
        assertThat(order.getSupplierCode()).isNotBlank();
        assertThat(order.getSupplierName()).isNotBlank();
        assertThat(order.getWarehouseId()).isEqualTo(seedWarehouseId());
        assertThat(order.getWarehouseCode()).isEqualTo(SEED_WAREHOUSE_CODE);
        assertThat(order.getWarehouseName()).isNotBlank();
        assertThat(order.getPurchaserId()).isNotNull();
        assertThat(order.getPlannedArrivalDate()).isNotNull();

        // 3.0000 × 6.2000 = 18.6000
        assertThat(order.getTotalAmount()).isEqualByComparingTo("18.6000");

        assertThat(order.getItems()).hasSize(1);
        PurchaseOrderItemVO item = order.getItems().getFirst();
        assertThat(item.getSkuId()).isEqualTo(skuId);
        assertThat(item.getPlannedQuantity()).isEqualByComparingTo("3.0000");
        assertThat(item.getPurchasePrice()).isEqualByComparingTo("6.2000");
        assertThat(item.getLineAmount()).isEqualByComparingTo("18.6000");
        assertThat(item.getPurchaseUnit()).isEqualTo(DEFAULT_PURCHASE_UNIT);
        assertThat(item.getSortOrder()).isZero();
        assertThat(item.getAllocations()).hasSize(1);
        assertThat(item.getAllocations().getFirst().getDemandId()).isEqualTo(demand.getId());

        // 需求侧：分配落账 → ALLOCATED，且供应商被固定到需求上（§7.4）
        PurchaseDemandEntity reloaded = reloadDemand(demand.getId());
        assertThat(reloaded.getAllocatedQuantity()).isEqualByComparingTo("3.0000");
        assertThat(reloaded.getStatus()).isEqualTo("ALLOCATED");
        assertThat(reloaded.getSupplierId()).isEqualTo(supplierId);

        // CREATE 日志：before 为空、after 是全量快照（§7.12）
        List<PurchaseOperationLogVO> logs = purchaseQueryService.orderLogs(order.getId());
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().getOperationType()).isEqualTo("CREATE");
        assertThat(logs.getFirst().getPurchaseOrderId()).isEqualTo(order.getId());
        assertThat(logs.getFirst().getBeforeData()).isNull();
        assertThat(logs.getFirst().getAfterData()).isNotNull();
        assertThat(logs.getFirst().getAfterData()).containsEntry("orderNo", order.getOrderNo());
    }

    // ------------------------------------------------------------------
    // 2. create 拒绝既有行 id
    // ------------------------------------------------------------------

    @Test
    @DisplayName("create：请求里带既有采购行 id → 40983（新建不允许「顺手改旧行」）")
    void createRejectsExistingItemId() {
        Long skuId = newOnShelfSku("PO2");
        Long supplierId = newPurchasableSupplier("PO2", skuId);
        Long customerId = newCustomer();
        Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
        PurchaseDemandEntity demand = generateDemandFor(supplierId, salesOrder);
        PurchaseOrderVO existing = createDraftOrder("PO2", supplierId, skuId, "3.0000", "6.2000",
                allocation(demand, "3.0000"));

        PurchaseOrderAddForm form = orderForm(supplierId, seedWarehouseId(), skuId, "3.0000", "6.2000",
                allocation(demand, "3.0000"));
        form.getItems().getFirst().setId(existing.getItems().getFirst().getId());

        expectCode(() -> purchaseOrderService.create(form, prefix + ":PO2:again"), 40983);
    }

    // ------------------------------------------------------------------
    // 3. create 未知需求
    // ------------------------------------------------------------------

    @Test
    @DisplayName("create：分配引用不存在的需求 → 40480")
    void createRejectsUnknownDemand() {
        Long skuId = newOnShelfSku("PO3");
        Long supplierId = newPurchasableSupplier("PO3", skuId);

        PurchaseOrderAddForm.Allocation ghost = new PurchaseOrderAddForm.Allocation();
        ghost.setDemandId(999_999_999L);
        ghost.setQuantity("1.0000");
        ghost.setDemandVersion(0);

        PurchaseOrderAddForm form = orderForm(supplierId, seedWarehouseId(), skuId, "1.0000", "1.0000", ghost);
        expectCode(() -> purchaseOrderService.create(form, prefix + ":PO3:po"), 40480);
    }

    // ------------------------------------------------------------------
    // 4. create 主数据停用
    // ------------------------------------------------------------------

    @Test
    @DisplayName("create：停用供应商 → 40986；停用仓库 → 40987")
    void createRejectsDisabledSupplierAndWarehouse() {
        Long skuId = newOnShelfSku("PO4");
        Long supplierId = newPurchasableSupplier("PO4", skuId);

        // 供应商：直改库置 DISABLED（W2 的启停不经过采购侧）
        assertThat(jdbc.update("UPDATE supplier SET status = 'DISABLED', version = version + 1 "
                + "WHERE id = ? AND deleted = FALSE", supplierId)).isEqualTo(1);
        evictMybatisCache();
        PurchaseOrderAddForm form = orderForm(supplierId, seedWarehouseId(), skuId, "1.0000", "1.0000");
        expectCode(() -> purchaseOrderService.create(form, prefix + ":PO4:po"), 40986);

        // 仓库：用基类的停用辅助，并把供应商改回 ENABLED
        assertThat(jdbc.update("UPDATE supplier SET status = 'ENABLED', version = version + 1 "
                + "WHERE id = ? AND deleted = FALSE", supplierId)).isEqualTo(1);
        evictMybatisCache();

        Long warehouseId = newWarehouse("PO4");
        disableWarehouse(warehouseId);
        PurchaseOrderAddForm second = orderForm(supplierId, warehouseId, skuId, "1.0000", "1.0000");
        expectCode(() -> purchaseOrderService.create(second, prefix + ":PO4:po2"), 40987);
    }

    // ------------------------------------------------------------------
    // 5. update 改数量 → 需求侧重算
    // ------------------------------------------------------------------

    @Test
    @DisplayName("update：改计划量 → 金额刷新、需求 allocated 重算、UPDATE 日志带 before/after")
    void updateRecomputesAmountAndDemand() {
        Long skuId = newOnShelfSku("PO5");
        Long supplierId = newPurchasableSupplier("PO5", skuId);
        Long customerId = newCustomer();
        Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
        PurchaseDemandEntity demand = generateDemandFor(supplierId, salesOrder);

        PurchaseOrderVO order = createDraftOrder("PO5", supplierId, skuId, "3.0000", "6.2000",
                allocation(demand, "1.0000"));
        assertThat(reloadDemand(demand.getId()).getStatus()).isEqualTo("PARTIALLY_ALLOCATED");

        PurchaseDemandEntity fresh = reloadDemand(demand.getId());
        PurchaseOrderUpdateForm form = editForm(order.getId(), "2.0000", "5.0000",
                allocation(fresh, "2.0000"));
        PurchaseOrderVO updated = purchaseOrderService.update(form);

        // 行：2.0000 × 5.0000 = 10.0000
        assertThat(updated.getTotalAmount()).isEqualByComparingTo("10.0000");
        assertThat(updated.getItems().getFirst().getPlannedQuantity()).isEqualByComparingTo("2.0000");
        assertThat(updated.getItems().getFirst().getPurchasePrice()).isEqualByComparingTo("5.0000");
        assertThat(updated.getItems().getFirst().getLineAmount()).isEqualByComparingTo("10.0000");
        // 版本推进
        assertThat(updated.getVersion()).isGreaterThan(order.getVersion());

        // 需求侧：1.0000 → 2.0000（不是叠加成 3.0000）
        PurchaseDemandEntity afterUpdate = reloadDemand(demand.getId());
        assertThat(afterUpdate.getAllocatedQuantity()).isEqualByComparingTo("2.0000");
        assertThat(afterUpdate.getStatus()).isEqualTo("PARTIALLY_ALLOCATED");

        // UPDATE 日志：before / after 都是全量快照。
        // 日志按 `created_at DESC, id DESC` 返回（与 W4 的 order_operation_log 一致）→ **最新在前**
        List<PurchaseOperationLogVO> logs = purchaseQueryService.orderLogs(order.getId());
        assertThat(logs).extracting(PurchaseOperationLogVO::getOperationType)
                .containsExactly("UPDATE", "CREATE");
        PurchaseOperationLogVO updateLog = logs.getFirst();
        assertThat(updateLog.getBeforeData()).isNotNull();
        assertThat(updateLog.getAfterData()).isNotNull();
        assertThat(updateLog.getBeforeData()).containsEntry("totalAmount", "18.6000");
        assertThat(updateLog.getAfterData()).containsEntry("totalAmount", "10.0000");
    }

    // ------------------------------------------------------------------
    // 6. update 保留行的已收数量
    // ------------------------------------------------------------------

    @Test
    @DisplayName("update：保留行必须继承库中 received_quantity —— 请求只表达计划量")
    void updateKeepsReceivedQuantityOfRetainedRow() {
        Long skuId = newOnShelfSku("PO6");
        Long supplierId = newPurchasableSupplier("PO6", skuId);
        Long customerId = newCustomer();
        Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
        PurchaseDemandEntity demand = generateDemandFor(supplierId, salesOrder);

        PurchaseOrderVO order = createDraftOrder("PO6", supplierId, skuId, "3.0000", "6.2000",
                allocation(demand, "3.0000"));
        Long itemId = order.getItems().getFirst().getId();

        // DRAFT 单的已收量现实中恒为 0，这里**故意**写一个非零值：
        // 若 update 依赖「DRAFT 已收恒为 0」这个可松动的隐含前提，就会把它静默清 0。
        assertThat(jdbc.update("UPDATE purchase_order_item SET received_quantity = 1.5000 "
                + "WHERE id = ? AND deleted = FALSE", itemId)).isEqualTo(1);
        evictMybatisCache();

        PurchaseDemandEntity fresh = reloadDemand(demand.getId());
        purchaseOrderService.update(editForm(order.getId(), "3.0000", "6.2000",
                allocation(fresh, "3.0000")));

        PurchaseOrderItemVO after = reloadOrder(order.getId()).getItems().getFirst();
        assertThat(after.getId()).isEqualTo(itemId);
        assertThat(after.getReceivedQuantity()).isEqualByComparingTo("1.5000");
        // 对账量随之派生：remaining = planned − received = 1.5000
        assertThat(after.getRemainingQuantity()).isEqualByComparingTo("1.5000");
        assertThat(after.getOverReceiptQuantity()).isEqualByComparingTo("0.0000");
    }

    // ------------------------------------------------------------------
    // 7. submit
    // ------------------------------------------------------------------

    @Test
    @DisplayName("submit：DRAFT → SUBMITTED；再次 submit → 40982")
    void submitMovesDraftToSubmittedAndRejectsSecondSubmit() {
        Long skuId = newOnShelfSku("PO7");
        Long supplierId = newPurchasableSupplier("PO7", skuId);
        Long customerId = newCustomer();
        Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
        PurchaseDemandEntity demand = generateDemandFor(supplierId, salesOrder);

        PurchaseOrderVO order = createDraftOrder("PO7", supplierId, skuId, "3.0000", "6.2000",
                allocation(demand, "3.0000"));

        PurchaseOrderVersionForm submit = new PurchaseOrderVersionForm();
        submit.setId(order.getId());
        submit.setVersion(order.getVersion());
        PurchaseOrderVO submitted = purchaseOrderService.submit(submit, prefix + ":PO7:submit");

        assertThat(submitted.getStatus()).isEqualTo("SUBMITTED");
        assertThat(submitted.getSubmittedAt()).isNotNull();
        assertThat(purchaseQueryService.orderLogs(order.getId()))
                .extracting(PurchaseOperationLogVO::getOperationType)
                .containsExactly("SUBMIT", "CREATE");

        // SUBMITTED 是终态之外的「不可编辑、不可重复提交」状态
        PurchaseOrderVersionForm again = new PurchaseOrderVersionForm();
        again.setId(submitted.getId());
        again.setVersion(submitted.getVersion());
        expectCode(() -> purchaseOrderService.submit(again, prefix + ":PO7:submit2"), 40982);
    }

    // ------------------------------------------------------------------
    // 8. cancel 释放分配
    // ------------------------------------------------------------------

    @Test
    @DisplayName("cancel：释放全部分配 + 需求回落 PENDING + 原因落日志")
    void cancelReleasesAllocationsAndDemandFallsBack() {
        Long skuId = newOnShelfSku("PO8");
        Long supplierId = newPurchasableSupplier("PO8", skuId);
        Long customerId = newCustomer();
        Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
        PurchaseDemandEntity demand = generateDemandFor(supplierId, salesOrder);

        PurchaseOrderVO order = createDraftOrder("PO8", supplierId, skuId, "3.0000", "6.2000",
                allocation(demand, "3.0000"));
        assertThat(reloadDemand(demand.getId()).getStatus()).isEqualTo("ALLOCATED");

        PurchaseOrderCancelForm cancel = new PurchaseOrderCancelForm();
        cancel.setId(order.getId());
        cancel.setVersion(order.getVersion());
        cancel.setCancelReason("供应商临时缺货");
        PurchaseOrderVO cancelled = purchaseOrderService.cancel(cancel, prefix + ":PO8:cancel");

        assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");
        assertThat(cancelled.getCancelledAt()).isNotNull();
        assertThat(cancelled.getCancelReason()).isEqualTo("供应商临时缺货");

        // 分配必须被释放：否则需求永久卡在 ALLOCATED（§7.8 C 段不变量）
        assertThat(allocationsOf(order.getId())).isEmpty();
        PurchaseDemandEntity afterCancel = reloadDemand(demand.getId());
        assertThat(afterCancel.getAllocatedQuantity()).isEqualByComparingTo("0.0000");
        assertThat(afterCancel.getStatus()).isEqualTo("PENDING");

        // 原因落进 CANCEL 日志的 reason 字段（不只是塞在 after_data 里）。DESC → 最新在最前
        PurchaseOperationLogVO cancelLog = purchaseQueryService.orderLogs(order.getId()).getFirst();
        assertThat(cancelLog.getOperationType()).isEqualTo("CANCEL");
        assertThat(cancelLog.getReason()).isEqualTo("供应商临时缺货");
    }

    // ------------------------------------------------------------------
    // 9. delete
    // ------------------------------------------------------------------

    @Test
    @DisplayName("delete：仅 DRAFT 可删（非草稿 → 40993），且删除本身幂等")
    void deleteOnlyAllowsDraftAndIsIdempotent() {
        Long skuId = newOnShelfSku("PO9");
        Long supplierId = newPurchasableSupplier("PO9", skuId);
        Long customerId = newCustomer();
        Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
        PurchaseDemandEntity demand = generateDemandFor(supplierId, salesOrder);

        // 草稿：可删，并释放分配
        PurchaseOrderVO draft = createDraftOrder("PO9", supplierId, skuId, "3.0000", "6.2000",
                allocation(demand, "3.0000"));
        PurchaseOrderDeleteForm delete = new PurchaseOrderDeleteForm();
        delete.setId(draft.getId());
        purchaseOrderService.delete(delete);

        assertThat(allocationsOf(draft.getId())).isEmpty();
        assertThat(reloadDemand(demand.getId()).getStatus()).isEqualTo("PENDING");
        // 幂等：再删一次不报错
        purchaseOrderService.delete(delete);

        // 非草稿：先 submit，再删 → 40993
        PurchaseDemandEntity secondDemand = generateDemandFor(supplierId, salesOrder);
        PurchaseOrderVO submitted = createDraftOrder("PO9b", supplierId, skuId, "1.0000", "6.2000",
                allocation(secondDemand, "1.0000"));
        PurchaseOrderVersionForm version = new PurchaseOrderVersionForm();
        version.setId(submitted.getId());
        version.setVersion(submitted.getVersion());
        purchaseOrderService.submit(version, prefix + ":PO9:submit");

        PurchaseOrderDeleteForm blocked = new PurchaseOrderDeleteForm();
        blocked.setId(submitted.getId());
        expectCode(() -> purchaseOrderService.delete(blocked), 40993);
    }

    // ------------------------------------------------------------------
    // 10. 状态机边界
    // ------------------------------------------------------------------

    @Test
    @DisplayName("状态机边界：DRAFT 不可少收关单（40982），非 DRAFT 不可编辑（40982）")
    void stateMachineBlocksShortCloseAndEditOutsideAllowedStates() {
        Long skuId = newOnShelfSku("PO10");
        Long supplierId = newPurchasableSupplier("PO10", skuId);
        Long customerId = newCustomer();
        Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
        PurchaseDemandEntity demand = generateDemandFor(supplierId, salesOrder);

        PurchaseOrderVO order = createDraftOrder("PO10", supplierId, skuId, "3.0000", "6.2000",
                allocation(demand, "3.0000"));

        // 少收关单只允许从 PARTIALLY_RECEIVED 出发（T5）
        PurchaseOrderShortCloseForm shortClose = new PurchaseOrderShortCloseForm();
        shortClose.setId(order.getId());
        shortClose.setVersion(order.getVersion());
        shortClose.setShortCloseReason("供应商只发一半");
        expectCode(() -> purchaseOrderService.shortClose(shortClose, prefix + ":PO10:sc"), 40982);

        // 提交之后不可再编辑行 / 分配（T2 只允许 DRAFT）
        PurchaseOrderVersionForm submit = new PurchaseOrderVersionForm();
        submit.setId(order.getId());
        submit.setVersion(order.getVersion());
        PurchaseOrderVO submitted = purchaseOrderService.submit(submit, prefix + ":PO10:submit");

        PurchaseDemandEntity fresh = reloadDemand(demand.getId());
        PurchaseOrderUpdateForm edit = editForm(submitted.getId(), "3.0000", "6.2000",
                allocation(fresh, "3.0000"));
        expectCode(() -> purchaseOrderService.update(edit), 40982);

        // 编辑失败不得改动需求侧
        assertThat(reloadDemand(demand.getId()).getAllocatedQuantity()).isEqualByComparingTo("3.0000");

        // 分配身份的唯一键：同一 (item, demand) 的第二条活动分配被库层拒绝
        PurchaseDemandAllocationEntity existing =
                allocationOf(submitted.getItems().getFirst().getId(), demand.getId());
        expectSqlFailure(
                "INSERT INTO purchase_demand_allocation (purchase_demand_id, purchase_order_item_id, "
                        + "sales_order_id, sales_order_item_id, sku_id, allocated_quantity, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, 1.0000, 'dup')",
                existing.getPurchaseDemandId(), existing.getPurchaseOrderItemId(),
                existing.getSalesOrderId(), existing.getSalesOrderItemId(), existing.getSkuId());

        // 未分配的需求也必须能进采购单：allocations 缺失 = 该行没有需求来源
        Long freeSkuId = newOnShelfSku("PO10b");
        Long freeSupplierId = newPurchasableSupplier("PO10b", freeSkuId);
        PurchaseOrderVO freeOrder = createDraftOrder("PO10b", freeSupplierId, freeSkuId,
                "2.0000", "3.0000");
        assertThat(freeOrder.getItems().getFirst().getAllocations()).isEmpty();
        assertThat(freeOrder.getTotalAmount()).isEqualByComparingTo("6.0000");
        assertThat(allocationsOf(freeOrder.getId())).isEmpty();
    }
}
