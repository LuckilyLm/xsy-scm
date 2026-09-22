package net.lab1024.sa.admin.module.scm.purchase;

import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderAddForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderBatchShortCloseForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderVersionForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptConfirmForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptItemWorkbenchQueryForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptItemVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptItemWorkbenchVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptVO;
import net.lab1024.sa.admin.module.scm.purchase.service.PurchaseQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 采购效率增强（Wave 2B §6.3 批量少收关单 / §6.3 按商品收货工作台）。
 *
 * <p>两块都是这一波新加、且必须在真实 PostgreSQL 上才成立的能力：批量关单走 {@code FOR UPDATE} 锁序
 * 与状态机，工作台走 {@code purchase_order_item} 上的聚合与 {@code SUBMITTED}/{@code PARTIALLY_RECEIVED}
 * 状态过滤，内存单测无法覆盖，因此整类落 IT。
 *
 * <p><b>批量原子性的可断言边界：</b>整批在同一个 {@code @Transactional} 里，基类用例本身也裹在一个
 * 事务内（默认 REQUIRED 传播，不开 SAVEPOINT），因此「非法成员令前面已处理的合法成员一并回滚」这一
 * 效果在事务内<b>不可观测</b>（要等最外层回滚）。故非法状态 / 版本冲突两例只断言抛出的业务码，
 * 不回读兄弟单的状态 —— 那会误测到同一未提交事务里前一成员残留的写入。原子性由服务方法上的
 * {@code @Transactional(rollbackFor)} 保证，属结构性事实。
 */
@DisplayName("批量少收关单 + 按商品收货工作台（PG IT）")
class PurchaseEfficiencyIT extends ScmW5PgITBase {

    @Autowired
    private PurchaseQueryService queryService;

    // ------------------------------------------------------------------
    // 造数辅助
    // ------------------------------------------------------------------

    /**
     * 造一张处于 {@code PARTIALLY_RECEIVED} 的采购单：两行两 SKU，A 收齐、B 欠收。
     *
     * <p>单行订单永远满足不了「至少一行已收 且 至少一行未收齐」，批量关单的合法成员必须是这种混合态。
     */
    private PurchaseOrderVO partiallyReceivedOrder(String suffix) {
        Long skuA = newOnShelfSku(suffix);
        Long skuB = newOnShelfSku(suffix + "b");
        Long supplierId = newSupplier(suffix);
        linkSupplierSkus(supplierId, skuA, skuB);

        Long customerId = newCustomer();
        Long soA = confirmedSalesOrder(customerId, skuA, "4.0000", "4.0000");
        Long soB = confirmedSalesOrder(customerId, skuB, "6.0000", "6.0000");
        PurchaseDemandEntity demandA = generateDemandFor(supplierId, soA);
        PurchaseDemandEntity demandB = generateDemandFor(supplierId, soB);

        PurchaseOrderAddForm form = new PurchaseOrderAddForm();
        form.setSupplierId(supplierId);
        form.setWarehouseId(seedWarehouseId());
        form.setPurchaserId(anyEmployeeId());
        form.setRemark("Wave2B 批量关单");
        form.setItems(new ArrayList<>(List.of(
                item(skuA, "4.0000", "6.2000", allocation(demandA, "4.0000")),
                item(skuB, "6.0000", "6.2000", allocation(demandB, "6.0000")))));
        PurchaseOrderVO order = purchaseOrderService.create(form, prefix + ":" + suffix + ":po");

        submitOrder(order.getId());
        PurchaseReceiptVO created = createReceipt(order.getId());
        PurchaseReceiptVO receipt = reloadReceipt(created.getId());
        List<PurchaseReceiptConfirmForm.Item> lines = new ArrayList<>(receipt.getItems().size());
        for (PurchaseReceiptItemVO line : receipt.getItems()) {
            // A（4.0000）收齐，B（6.0000）只收 2.0000 → 混合收付
            String quantity = line.getSkuId().equals(skuA) ? "4.0000" : "2.0000";
            lines.add(receiptLine(line.getId(), line.getVersion(), quantity));
        }
        purchaseReceiptService.confirm(
                confirmForm(receipt.getId(), receipt.getVersion(),
                        lines.toArray(new PurchaseReceiptConfirmForm.Item[0])),
                prefix + ":" + suffix + ":rc");

        PurchaseOrderVO partially = reloadOrder(order.getId());
        assertThat(partially.getStatus()).isEqualTo("PARTIALLY_RECEIVED");
        return partially;
    }

    /**
     * 造一张 {@code RECEIVED} 的采购单（单 SKU 全收）：状态机不允许再关单，用作批量里的非法成员。
     */
    private PurchaseOrderVO fullyReceivedOrder(String suffix) {
        Long skuId = newOnShelfSku(suffix);
        Long supplierId = newPurchasableSupplier(suffix, skuId);
        PurchaseOrderVO order = createDraftOrder(suffix, supplierId, skuId, "5.0000", "6.2000");
        submitOrder(order.getId());
        PurchaseReceiptVO created = createReceipt(order.getId());
        PurchaseReceiptVO receipt = reloadReceipt(created.getId());
        PurchaseReceiptItemVO line = receipt.getItems().getFirst();
        purchaseReceiptService.confirm(
                confirmForm(receipt.getId(), receipt.getVersion(),
                        receiptLine(line.getId(), line.getVersion(), "5.0000")),
                prefix + ":" + suffix + ":rc");
        PurchaseOrderVO received = reloadOrder(order.getId());
        assertThat(received.getStatus()).isEqualTo("RECEIVED");
        return received;
    }

    private PurchaseOrderBatchShortCloseForm batchForm(String reason, PurchaseOrderVO... orders) {
        List<PurchaseOrderVersionForm> rows = new ArrayList<>(orders.length);
        for (PurchaseOrderVO order : orders) {
            PurchaseOrderVersionForm row = new PurchaseOrderVersionForm();
            row.setId(order.getId());
            row.setVersion(order.getVersion());
            rows.add(row);
        }
        PurchaseOrderBatchShortCloseForm form = new PurchaseOrderBatchShortCloseForm();
        form.setOrders(rows);
        form.setShortCloseReason(reason);
        return form;
    }

    private PurchaseReceiptItemWorkbenchQueryForm workbench(Long supplierId) {
        PurchaseReceiptItemWorkbenchQueryForm form = new PurchaseReceiptItemWorkbenchQueryForm();
        form.setSupplierId(supplierId);
        form.setWarehouseId(seedWarehouseId());
        form.setPageNum(1L);
        form.setPageSize(50L);
        return form;
    }

    // ------------------------------------------------------------------
    // 批量少收关单
    // ------------------------------------------------------------------

    @Test
    @DisplayName("两张欠收单 → 批量关单全部转 SHORT_CLOSED，共享同一原因")
    void batchShortCloseClosesAllLegalOrders() {
        PurchaseOrderVO first = partiallyReceivedOrder("BC1");
        PurchaseOrderVO second = partiallyReceivedOrder("BC2");

        purchaseOrderService.batchShortClose(batchForm("供应商产能不足，剩余不再补", first, second));

        for (PurchaseOrderVO order : List.of(first, second)) {
            PurchaseOrderVO closed = reloadOrder(order.getId());
            assertThat(closed.getStatus()).isEqualTo("SHORT_CLOSED");
            assertThat(closed.getShortCloseReason()).isEqualTo("供应商产能不足，剩余不再补");
            assertThat(closed.getShortClosedAt()).isNotNull();
        }
    }

    @Test
    @DisplayName("批量含一张全收单 → 状态机拒绝，抛 40982")
    void batchShortCloseRejectsIllegalStateMember() {
        PurchaseOrderVO partially = partiallyReceivedOrder("BC3");
        PurchaseOrderVO received = fullyReceivedOrder("BC3r");

        expectCode(() -> purchaseOrderService.batchShortClose(
                batchForm("整批关单", partially, received)), 40982);
    }

    @Test
    @DisplayName("批量含一张版本过期单 → 乐观锁拒绝，抛 40921")
    void batchShortCloseRejectsStaleVersionMember() {
        PurchaseOrderVO first = partiallyReceivedOrder("BC4");
        PurchaseOrderVO second = partiallyReceivedOrder("BC5");
        // 让低 id 那张带一个不存在的版本：按 id 升序处理时先撞上版本冲突
        PurchaseOrderVO stale = first.getId() < second.getId() ? first : second;
        stale.setVersion(stale.getVersion() + 999);

        expectCode(() -> purchaseOrderService.batchShortClose(
                batchForm("整批关单", first, second)), 40921);
    }

    // ------------------------------------------------------------------
    // 按商品收货工作台（只读聚合）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("单张欠收单：待收 = 计划 − 已收，超收 0，订单数 / 行数各 1")
    void workbenchShowsPendingPerSku() {
        PurchaseOrderVO order = partiallyReceivedOrder("WB1");
        // 取该单里欠收的那一行（SKU B）—— 它才有非零待收
        PurchaseOrderItemView target = pendingLineOf(order);

        PurchaseReceiptItemWorkbenchVO row = workbenchRow(target.skuId, target.supplierId);

        assertThat(row.getLineCount()).isEqualTo(1L);
        assertThat(row.getOrderCount()).isEqualTo(1L);
        assertThat(row.getPlannedQuantity()).isEqualByComparingTo("6.0000");
        assertThat(row.getReceivedQuantity()).isEqualByComparingTo("2.0000");
        assertThat(row.getPendingQuantity()).isEqualByComparingTo("4.0000");
        assertThat(row.getOverReceiptQuantity()).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("同一 SKU 跨两张欠收单：工作台按 SKU×单位 汇总计划 / 已收 / 待收")
    void workbenchAggregatesAcrossOrders() {
        Long skuId = newOnShelfSku("WB2");
        Long supplierId = newPurchasableSupplier("WB2", skuId);

        for (int i = 0; i < 2; i++) {
            String suffix = "WB2-" + i;
            PurchaseOrderVO order = createDraftOrder(suffix, supplierId, skuId, "10.0000", "6.2000");
            submitOrder(order.getId());
            PurchaseReceiptVO created = createReceipt(order.getId());
            PurchaseReceiptVO receipt = reloadReceipt(created.getId());
            PurchaseReceiptItemVO line = receipt.getItems().getFirst();
            // 每张收 3.0000，欠 7.0000
            purchaseReceiptService.confirm(
                    confirmForm(receipt.getId(), receipt.getVersion(),
                            receiptLine(line.getId(), line.getVersion(), "3.0000")),
                    prefix + ":" + suffix + ":rc");
        }

        PurchaseReceiptItemWorkbenchVO row = workbenchRow(skuId, supplierId);

        assertThat(row.getOrderCount()).isEqualTo(2L);
        assertThat(row.getLineCount()).isEqualTo(2L);
        assertThat(row.getPlannedQuantity()).isEqualByComparingTo("20.0000");
        assertThat(row.getReceivedQuantity()).isEqualByComparingTo("6.0000");
        assertThat(row.getPendingQuantity()).isEqualByComparingTo("14.0000");
        assertThat(row.getOverReceiptQuantity()).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("全收齐（RECEIVED）订单不进工作台：状态过滤只覆盖 SUBMITTED / PARTIALLY_RECEIVED")
    void workbenchExcludesReceivedOrders() {
        PurchaseOrderVO received = fullyReceivedOrder("WB3");
        Long skuId = received.getItems().getFirst().getSkuId();
        Long supplierId = received.getSupplierId();

        List<PurchaseReceiptItemWorkbenchVO> rows =
                queryService.receiptItemWorkbench(workbench(supplierId)).getList();

        assertThat(rows).noneMatch(r -> r.getSkuId().equals(skuId));
    }

    // ------------------------------------------------------------------
    // 工作台读侧小工具
    // ------------------------------------------------------------------

    private record PurchaseOrderItemView(Long skuId, Long supplierId) {
    }

    /**
     * 从一张混合收付单里挑出<b>欠收</b>的那一行（{@code PARTIALLY_RECEIVED} 下必存在）。
     */
    private PurchaseOrderItemView pendingLineOf(PurchaseOrderVO order) {
        return order.getItems().stream()
                .filter(it -> it.getReceivedQuantity() != null
                        && it.getReceivedQuantity().compareTo(it.getPlannedQuantity()) < 0)
                .findFirst()
                .map(it -> new PurchaseOrderItemView(it.getSkuId(), order.getSupplierId()))
                .orElseThrow(() -> new AssertionError("混合单应存在一行欠收"));
    }

    private PurchaseReceiptItemWorkbenchVO workbenchRow(Long skuId, Long supplierId) {
        return queryService.receiptItemWorkbench(workbench(supplierId)).getList().stream()
                .filter(r -> r.getSkuId().equals(skuId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("工作台缺少 SKU " + skuId + " 的汇总行"));
    }
}
