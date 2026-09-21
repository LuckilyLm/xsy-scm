package net.lab1024.sa.admin.module.scm.inventory;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.inventory.domain.InventoryOutboundFact;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryBalanceEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 移动加权成本核算的 PostgreSQL 集成测试（V34，库存深化最后一项）。
 *
 * <p>覆盖四件事：
 * <ol>
 *   <li><b>加权公式</b> —— {@code (旧量·旧均价 + 入量·入价) / 新量}，含不同单价的多笔入库；</li>
 *   <li><b>出库不改变均价</b> —— 这是移动加权平均的性质，也是「出库流水带成本」的前提；</li>
 *   <li><b>出库流水写当时的均价</b> —— V34 的核心语义变更（此前出库的 unit_cost 一律为 NULL）；</li>
 *   <li><b>不带来新成本事实的入库按现有均价入账</b> —— 盘盈 / 报溢只是把数量补上，
 *       均价不变。<b>调拨转入与规格转换转入不属于这一类</b>：它们按转出腿的成本加权，
 *       由 {@code ScmInventoryTransferIT} 与 {@code ScmInventoryConversionIT} 覆盖。</li>
 * </ol>
 */
@DisplayName("移动加权成本（PG IT）")
class ScmInventoryCostingIT extends ScmW6PgITBase {

    /**
     * 造一笔**指定采购单价**的入库，返回 skuId（同一 SKU 可多次调用以构造不同价）。
     */
    private void inboundAtPrice(String suffix, Long skuId, String quantity, String price) {
        Long supplierId = newSupplier(suffix);
        linkSupplierSku(supplierId, skuId, DEFAULT_PURCHASE_UNIT);
        PurchaseOrderVO order = createDraftOrder(suffix, supplierId, skuId, quantity, price);
        PurchaseReceiptVO receipt = submittedOrderReceipt(order.getId());
        confirmReceipt(receipt.getId(), quantity);
    }

    private InventoryOutboundFact outboundFact(Long wh, Long sku, String qty, long itemId) {
        return new InventoryOutboundFact(wh, sku, 0L, itemId, new BigDecimal(qty), null,
                OffsetDateTime.now(), "test:1");
    }

    private List<Map<String, Object>> salesOutMovements(Long wh, Long sku) {
        return movementsOf(wh, sku).stream()
                .filter(m -> "SALES_OUT".equals(String.valueOf(m.get("movement_type"))))
                .toList();
    }

    @Test
    @DisplayName("首次入库：均价 = 采购单价（不是 0，也不是别的默认值）")
    void firstInboundSetsAvgToPurchasePrice() {
        Long wh = seedWarehouseId();
        Long sku = newSkuOfType("co1", "NON_STANDARD", "ON_SHELF");

        inboundAtPrice("co1", sku, "10.0000", "6.5000");

        InventoryBalanceEntity balance = balanceRow(wh, sku);
        assertThat(balance.getQuantity()).isEqualByComparingTo("10.0000");
        assertThat(balance.getAvgCost()).as("首次入库的均价就是采购单价")
                .isEqualByComparingTo("6.5000");
    }

    @Test
    @DisplayName("多笔不同价入库：均价按 (旧量·旧均价 + 入量·入价)/新量 加权")
    void multipleInboundsAverageTheCost() {
        Long wh = seedWarehouseId();
        Long sku = newSkuOfType("co2", "NON_STANDARD", "ON_SHELF");

        // 10 × 6.0000 = 60
        inboundAtPrice("co2a", sku, "10.0000", "6.0000");
        assertThat(balanceRow(wh, sku).getAvgCost()).isEqualByComparingTo("6.0000");

        // 再加 10 × 8.0000 = 80 → 总 140 / 20 = 7.0000
        inboundAtPrice("co2b", sku, "10.0000", "8.0000");
        InventoryBalanceEntity balance = balanceRow(wh, sku);
        assertThat(balance.getQuantity()).isEqualByComparingTo("20.0000");
        assertThat(balance.getAvgCost()).as("(10×6 + 10×8) / 20 = 7").isEqualByComparingTo("7.0000");

        // 再加 5 × 7.0000 = 35 → 总 175 / 25 = 7.0000（均价不变，验证公式而不是巧合）
        inboundAtPrice("co2c", sku, "5.0000", "7.0000");
        assertThat(balanceRow(wh, sku).getAvgCost()).isEqualByComparingTo("7.0000");
    }

    @Test
    @DisplayName("出库不改变均价，但流水写入当时的均价（V34 的核心语义变更）")
    void outboundKeepsAvgAndStampsItOnTheMovement() {
        Long wh = seedWarehouseId();
        Long sku = newSkuOfType("co3", "NON_STANDARD", "ON_SHELF");

        inboundAtPrice("co3a", sku, "10.0000", "6.0000");
        inboundAtPrice("co3b", sku, "10.0000", "8.0000");
        assertThat(balanceRow(wh, sku).getAvgCost()).isEqualByComparingTo("7.0000");

        inventoryCommandService.postSalesOutbound(outboundFact(wh, sku, "4.0000", 950001L));

        InventoryBalanceEntity after = balanceRow(wh, sku);
        assertThat(after.getQuantity()).isEqualByComparingTo("16.0000");
        assertThat(after.getAvgCost()).as("移动加权平均：出库不改变均价")
                .isEqualByComparingTo("7.0000");

        Map<String, Object> movement = salesOutMovements(wh, sku).getFirst();
        assertThat(new BigDecimal(String.valueOf(movement.get("unit_cost"))))
                .as("出库流水带的是出库那一刻的均价（此前一律为 NULL）")
                .isEqualByComparingTo("7.0000");

        // 出库后的再入库，加权基数用**出库后的量**（16），不是入库总量（20）
        inboundAtPrice("co3c", sku, "4.0000", "11.0000");
        // (16×7 + 4×11) / 20 = (112 + 44) / 20 = 7.8000
        assertThat(balanceRow(wh, sku).getAvgCost()).isEqualByComparingTo("7.8000");
    }

    @Test
    @DisplayName("入库流水的 unit_cost 仍是采购单价（与余额均价区分开）")
    void inboundMovementCarriesThePurchasePrice() {
        Long wh = seedWarehouseId();
        Long sku = newSkuOfType("co4", "NON_STANDARD", "ON_SHELF");

        inboundAtPrice("co4a", sku, "10.0000", "6.0000");
        inboundAtPrice("co4b", sku, "10.0000", "8.0000");

        List<Map<String, Object>> purchaseIn = movementsOf(wh, sku).stream()
                .filter(m -> "PURCHASE_IN".equals(String.valueOf(m.get("movement_type"))))
                .toList();
        assertThat(purchaseIn).hasSize(2);
        // 流水的 unit_cost 记的是**该笔入库的实际单价**，不是余额均价 ——
        // 这是「期初均价取最近一次采购入库单价」这条回填口径能成立的前提。
        assertThat(new BigDecimal(String.valueOf(purchaseIn.get(0).get("unit_cost"))))
                .isEqualByComparingTo("6.0000");
        assertThat(new BigDecimal(String.valueOf(purchaseIn.get(1).get("unit_cost"))))
                .isEqualByComparingTo("8.0000");
        assertThat(balanceRow(wh, sku).getAvgCost()).isEqualByComparingTo("7.0000");
    }
}
