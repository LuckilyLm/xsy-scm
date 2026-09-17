package net.lab1024.sa.admin.module.scm.purchase;

import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderAddForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderShortCloseForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptConfirmForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptItemVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 少收关单（W5 Target Design §4.2 T5 / R12 / G1，3 例）。
 *
 * <p><b>`shortClose` 的准入条件是两个「至少」</b>：至少一行**已收**，且至少一行**未收齐**。
 * 缺一都不成立 ——
 * <ul>
 *   <li>没有任何已收 → 那是「取消」，不是「少收」（`cancellable` 管的是 DRAFT/SUBMITTED）；</li>
 *   <li>全部收齐 → 采购单已经是 {@code RECEIVED}，状态机根本不允许再关单。</li>
 * </ul>
 * 所以本类的前置必须造**两行**：一行收齐、一行欠收。单行订单永远测不出这条规则。
 *
 * <p><b>R12 / G1：少收关单**不**释放需求分配</b>。这与 `cancel` / `delete` 相反，是**有意**的 ——
 * 少收意味着「货已经到了一部分」，需求与采购的关联是既成事实；把分配撤掉会让
 * 「已收的货对应哪个客户需求」失去依据。冲销口径（多收/少收怎么结算）不在 W5 范围内。
 */
@DisplayName("少收关单：混合收付状态 + 分配保留（PG IT）")
class PurchaseReceiptShortCloseIT extends ScmW5PgITBase {

    /** 两行采购单（两个 SKU）+ 一张草稿收货单。 */
    private record MixedFixture(Long skuA, Long skuB, Long supplierId,
                                PurchaseDemandEntity demandA, PurchaseDemandEntity demandB,
                                PurchaseOrderVO order, PurchaseReceiptVO receipt) {
    }

    private MixedFixture mixed(String suffix) {
        Long skuA = newOnShelfSku(suffix);
        Long skuB = newOnShelfSku(suffix + "b");
        Long supplierId = newSupplier(suffix);
        // 两个 SKU 必须一次挂完：replace 是整体替换
        linkSupplierSkus(supplierId, skuA, skuB);

        Long customerId = newCustomer();
        Long salesOrderA = confirmedSalesOrder(customerId, skuA, "4.0000", "4.0000");
        Long salesOrderB = confirmedSalesOrder(customerId, skuB, "6.0000", "6.0000");
        PurchaseDemandEntity demandA = generateDemandFor(supplierId, salesOrderA);
        PurchaseDemandEntity demandB = generateDemandFor(supplierId, salesOrderB);

        PurchaseOrderAddForm form = new PurchaseOrderAddForm();
        form.setSupplierId(supplierId);
        form.setWarehouseId(seedWarehouseId());
        form.setPurchaserId(anyEmployeeId());
        form.setRemark("W5 少收关单用例");
        form.setItems(new ArrayList<>(List.of(
                item(skuA, "4.0000", "6.2000", allocation(demandA, "4.0000")),
                item(skuB, "6.0000", "6.2000", allocation(demandB, "6.0000")))));
        PurchaseOrderVO order = purchaseOrderService.create(form, prefix + ":" + suffix + ":po");

        PurchaseReceiptVO receipt = submittedOrderReceipt(order.getId());
        return new MixedFixture(skuA, skuB, supplierId, demandA, demandB, order, receipt);
    }

    /** 按 SKU 分派两个数量地确认收货。 */
    private PurchaseReceiptVO confirm(MixedFixture fx, String quantityA, String quantityB) {
        PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
        List<PurchaseReceiptConfirmForm.Item> items = new ArrayList<>(current.getItems().size());
        for (PurchaseReceiptItemVO line : current.getItems()) {
            String quantity = line.getSkuId().equals(fx.skuA()) ? quantityA : quantityB;
            items.add(receiptLine(line.getId(), line.getVersion(), quantity));
        }
        return purchaseReceiptService.confirm(
                confirmForm(current.getId(), current.getVersion(),
                        items.toArray(new PurchaseReceiptConfirmForm.Item[0])),
                prefix + ":sc:" + quantityA + ":" + quantityB);
    }

    private PurchaseOrderVO shortClose(MixedFixture fx, String reason) {
        PurchaseOrderShortCloseForm form = new PurchaseOrderShortCloseForm();
        form.setId(fx.order().getId());
        form.setVersion(reloadOrder(fx.order().getId()).getVersion());
        form.setShortCloseReason(reason);
        return purchaseOrderService.shortClose(form, prefix + ":sc:close:" + fx.order().getId());
    }

    // ------------------------------------------------------------------
    // 1. 混合状态 → 允许关单
    // ------------------------------------------------------------------

    @Test
    @DisplayName("一行收齐 + 一行欠收 → shortClose 成功，状态 SHORT_CLOSED 且原因落库")
    void shortCloseAfterPartialReceiptOnMixedLines() {
        MixedFixture fx = mixed("SC1");
        assertThat(fx.order().getItems()).hasSize(2);

        // A 收齐 4.0000，B 只收 2.0000（还差 4.0000）
        confirm(fx, "4.0000", "2.0000");
        PurchaseOrderVO partially = reloadOrder(fx.order().getId());
        assertThat(partially.getStatus()).isEqualTo("PARTIALLY_RECEIVED");
        assertThat(partially.getItems()).allSatisfy(item -> assertThat(item.getReceivedQuantity()).isPositive());

        PurchaseOrderVO closed = shortClose(fx, "供应商只发部分，剩余不再补");
        assertThat(closed.getStatus()).isEqualTo("SHORT_CLOSED");
        assertThat(closed.getShortClosedAt()).isNotNull();
        assertThat(closed.getShortCloseReason()).isEqualTo("供应商只发部分，剩余不再补");

        // 关单不抹掉已收数量与欠收差异
        assertThat(closed.getItems()).extracting(item -> item.getRemainingQuantity())
                .containsExactlyInAnyOrder(
                        new java.math.BigDecimal("0.0000"),
                        new java.math.BigDecimal("4.0000"));

        // SHORT_CLOSE 日志带原因
        assertThat(purchaseQueryService.orderLogs(fx.order().getId()))
                .filteredOn(log -> "SHORT_CLOSE".equals(log.getOperationType()))
                .singleElement()
                .satisfies(log -> assertThat(log.getReason()).isEqualTo("供应商只发部分，剩余不再补"));
    }

    // ------------------------------------------------------------------
    // 2. 全部收齐 → 拒绝
    // ------------------------------------------------------------------

    @Test
    @DisplayName("全部行收齐 → 采购单已是 RECEIVED，shortClose → 40982")
    void shortCloseRejectedWhenAllLinesReceived() {
        MixedFixture fx = mixed("SC2");
        confirm(fx, "4.0000", "6.0000");
        assertThat(reloadOrder(fx.order().getId()).getStatus()).isEqualTo("RECEIVED");

        PurchaseOrderShortCloseForm form = new PurchaseOrderShortCloseForm();
        form.setId(fx.order().getId());
        form.setVersion(reloadOrder(fx.order().getId()).getVersion());
        form.setShortCloseReason("已经全收，没有少收");
        expectCode(() -> purchaseOrderService.shortClose(form, prefix + ":SC2:close"), 40982);

        assertThat(reloadOrder(fx.order().getId()).getStatus()).isEqualTo("RECEIVED");
    }

    // ------------------------------------------------------------------
    // 3. R12 / G1：关单不释放分配
    // ------------------------------------------------------------------

    @Test
    @DisplayName("R12 / G1：shortClose **不**释放需求分配（与 cancel / delete 相反）")
    void shortCloseKeepsAllocations() {
        MixedFixture fx = mixed("SC3");
        confirm(fx, "4.0000", "2.0000");
        assertThat(allocationsOf(fx.order().getId())).hasSize(2);

        PurchaseOrderVO closed = shortClose(fx, "剩余不再到货");
        assertThat(closed.getStatus()).isEqualTo("SHORT_CLOSED");

        // 分配仍在：已收的货仍能追溯到客户需求
        assertThat(allocationsOf(fx.order().getId())).hasSize(2);
        assertThat(reloadDemand(fx.demandA().getId()).getStatus()).isEqualTo("ALLOCATED");
        assertThat(reloadDemand(fx.demandB().getId()).getStatus()).isEqualTo("ALLOCATED");
        assertThat(reloadDemand(fx.demandA().getId()).getAllocatedQuantity()).isEqualByComparingTo("4.0000");
        assertThat(reloadDemand(fx.demandB().getId()).getAllocatedQuantity()).isEqualByComparingTo("6.0000");
    }
}
