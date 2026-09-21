package net.lab1024.sa.admin.module.scm.warehouse;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.purchase.constant.ScmReceiptModeEnum;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseDemandGenerateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptCreateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptVO;
import net.lab1024.sa.admin.module.scm.warehouse.domain.form.WarehouseStatusForm;
import net.lab1024.sa.admin.module.scm.warehouse.service.WarehouseQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 仓库启停生命周期（B1，HD-B1-01 严格模式，PG IT）。
 *
 * <p>停用有三条阻塞条件（按序短路）：库存余额、在途采购单（SUBMITTED/PARTIALLY_RECEIVED）、
 * 待入库收货单（CONFIRMED + putaway=PENDING）。停用后：历史查询/详情照常，新业务引用一律拒绝。
 */
@DisplayName("仓库启停生命周期（PG IT）")
class WarehouseLifecycleIT extends ScmW6PgITBase {

    @Autowired
    private WarehouseQueryService warehouseQueryService;

    /**
     * 读库中的权威 version（jdbc 直读，绕开 MyBatis 一级缓存）。
     */
    private WarehouseStatusForm statusForm(Long id) {
        WarehouseStatusForm form = new WarehouseStatusForm();
        form.setId(id);
        form.setVersion(jdbc.queryForObject(
                "SELECT version FROM warehouse WHERE id = ?", Integer.class, id));
        return form;
    }

    private String warehouseStatus(Long id) {
        return jdbc.queryForObject("SELECT status FROM warehouse WHERE id = ?", String.class, id);
    }

    /**
     * 造一张绑定到指定仓库的已提交采购单（无需求来源）。
     */
    private PurchaseOrderVO submittedOrderOn(Long warehouseId, String suffix, String quantity) {
        Long skuId = newOnShelfSku(suffix);
        Long supplierId = newPurchasableSupplier(suffix, skuId);
        PurchaseOrderVO order = purchaseOrderService.create(
                orderForm(supplierId, warehouseId, skuId, quantity, "6.2000"),
                prefix + ":" + suffix + ":po");
        return submitOrder(order.getId());
    }

    /**
     * 为采购单造一张 WAREHOUSE_CONFIRM 收货单。
     */
    private PurchaseReceiptVO warehouseConfirmReceipt(Long orderId, String suffix) {
        PurchaseReceiptCreateForm form = new PurchaseReceiptCreateForm();
        form.setPurchaseOrderId(orderId);
        form.setReceiptMode(ScmReceiptModeEnum.WAREHOUSE_CONFIRM.name());
        form.setRemark("WL IT 收货单");
        return purchaseReceiptService.create(form, prefix + ":" + suffix + ":receipt");
    }

    // ------------------------------------------------------------------
    // 1. 启停往返 + 状态非法
    // ------------------------------------------------------------------

    @Test
    @DisplayName("启停往返：disable → enable；重复同向操作 41004")
    void enableDisableRoundTrip() {
        Long w = newWarehouse("WL1");
        assertThat(warehouseStatus(w)).isEqualTo("ENABLED");

        warehouseService.disable(statusForm(w));
        assertThat(warehouseStatus(w)).isEqualTo("DISABLED");
        expectCode(() -> warehouseService.disable(statusForm(w)), 41004);

        warehouseService.enable(statusForm(w));
        assertThat(warehouseStatus(w)).isEqualTo("ENABLED");
        expectCode(() -> warehouseService.enable(statusForm(w)), 41004);
    }

    // ------------------------------------------------------------------
    // 2. 停用阻塞：库存余额
    // ------------------------------------------------------------------

    @Test
    @DisplayName("停用阻塞：有库存余额 → 41005")
    void balanceBlocksDisable() {
        Long w = newWarehouse("WL2");
        PurchaseOrderVO order = submittedOrderOn(w, "WL2", "5.0000");
        confirmReceipt(createReceipt(order.getId()).getId(), "5.0000");
        assertThat(balanceRow(w, order.getItems().getFirst().getSkuId()).getQuantity())
                .isEqualByComparingTo("5.0000");

        expectCode(() -> warehouseService.disable(statusForm(w)), 41005);
    }

    // ------------------------------------------------------------------
    // 3. 停用阻塞：在途采购单
    // ------------------------------------------------------------------

    @Test
    @DisplayName("停用阻塞：SUBMITTED 采购单 → 41006")
    void submittedOrderBlocksDisable() {
        Long w = newWarehouse("WL3");
        submittedOrderOn(w, "WL3", "5.0000");
        expectCode(() -> warehouseService.disable(statusForm(w)), 41006);
    }

    @Test
    @DisplayName("停用阻塞：PARTIALLY_RECEIVED 采购单 → 41006")
    void partiallyReceivedOrderBlocksDisable() {
        Long w = newWarehouse("WL4");
        PurchaseOrderVO order = submittedOrderOn(w, "WL4", "10.0000");
        PurchaseReceiptVO receipt = warehouseConfirmReceipt(order.getId(), "WL4");
        confirmReceipt(receipt.getId(), "4.0000");
        assertThat(reloadOrder(order.getId()).getStatus()).isEqualTo("PARTIALLY_RECEIVED");

        expectCode(() -> warehouseService.disable(statusForm(w)), 41006);
    }

    // ------------------------------------------------------------------
    // 4. 停用阻塞：待入库收货单
    // ------------------------------------------------------------------

    @Test
    @DisplayName("停用阻塞：CONFIRMED 且 putaway=PENDING 收货单 → 41007")
    void pendingPutawayBlocksDisable() {
        Long w = newWarehouse("WL5");
        PurchaseOrderVO order = submittedOrderOn(w, "WL5", "5.0000");
        PurchaseReceiptVO receipt = warehouseConfirmReceipt(order.getId(), "WL5");
        confirmReceipt(receipt.getId(), "5.0000");
        assertThat(reloadOrder(order.getId()).getStatus()).isEqualTo("RECEIVED");
        assertThat(reloadReceipt(receipt.getId()).getPutawayStatus()).isEqualTo("PENDING");

        expectCode(() -> warehouseService.disable(statusForm(w)), 41007);
    }

    // ------------------------------------------------------------------
    // 5. 停用后：新引用拒绝，历史查询/详情照常
    // ------------------------------------------------------------------

    @Test
    @DisplayName("停用后：新需求/新采购单拒绝（40987），历史详情与选择器口径正确")
    void disabledWarehouseRejectsNewReferencesButAllowsHistory() {
        Long w = newWarehouse("WL6");
        warehouseService.disable(statusForm(w));

        // 新采购需求 → 40987
        PurchaseDemandGenerateForm gen = new PurchaseDemandGenerateForm();
        gen.setStartAt(OffsetDateTime.now().minusMinutes(1));
        gen.setEndAt(OffsetDateTime.now());
        gen.setWarehouseId(w);
        expectCode(() -> purchaseDemandService.generate(gen, prefix + ":WL6:gen"), 40987);

        // 新采购单 → 40987
        Long skuId = newOnShelfSku("WL6");
        Long supplierId = newPurchasableSupplier("WL6", skuId);
        expectCode(() -> purchaseOrderService.create(
                orderForm(supplierId, w, skuId, "5.0000", "6.2000"), prefix + ":WL6:po"), 40987);

        // 历史详情照常（detail 不校验启用态）
        assertThat(warehouseQueryService.detail(w).getStatus()).isEqualTo("DISABLED");
        // 下拉选择器只列 ENABLED → 不含该停用仓库
        assertThat(warehouseQueryService.list()).noneMatch(x -> x.getId().equals(w));
    }

    @Test
    @DisplayName("停用后：新收货单拒绝（40987）")
    void disabledWarehouseRejectsNewReceipt() {
        Long w = newWarehouse("WL7");
        PurchaseOrderVO order = submittedOrderOn(w, "WL7", "5.0000");
        // 模拟「强制退役」：越过停用守卫直接置 DISABLED（正常流程因在途采购单被 41006 拦截）
        disableWarehouse(w);

        expectCode(() -> createReceipt(order.getId()), 40987);
    }
}
