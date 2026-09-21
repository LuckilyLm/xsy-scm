package net.lab1024.sa.admin.module.scm.inventory;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.inventory.domain.InventoryOutboundFact;
import net.lab1024.sa.admin.module.scm.inventory.domain.ReserveInventoryFact;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryBalanceEntity;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryReservationService;
import net.lab1024.sa.admin.module.scm.warehouse.service.WarehouseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 出库与预留的 PostgreSQL 集成测试（出库波次）。
 *
 * <p>覆盖三件在单测里验证不了的事：
 * <ol>
 *   <li><b>方向感知的快照约束</b> —— 出库流水必须满足 {@code after = before - quantity}，
 *       这是 V25 重建 {@code ck_inventory_movement_snap} 的直接原因；</li>
 *   <li><b>可用量门槛</b> —— 出库不能吃掉已预留的货，DB 的
 *       {@code ck_inventory_balance_available} 与服务层的 41011 双重把关；</li>
 *   <li><b>append-only 对出库同样生效</b> —— 新增流水类型不能成为绕过 Q7 的口子。</li>
 * </ol>
 */
@DisplayName("出库与预留（PG IT）")
class ScmInventoryOutboundIT extends ScmW6PgITBase {

    @Autowired
    private InventoryReservationService reservations;

    @Autowired
    private WarehouseService warehouseService;

    private Long warehouseId() {
        return warehouseService.defaultEnabledWarehouse().getId();
    }

    /**
     * 造一个已入库指定数量的 SKU，返回 (warehouseId, skuId)。
     */
    private Object[] stocked(String suffix, String quantity) {
        Long skuId = newSkuOfType(suffix, "NON_STANDARD", "ON_SHELF");
        W6Fixture fixture = inboundFixture(suffix, skuId, quantity);
        confirmReceipt(fixture.receipt().getId(), quantity);
        return new Object[]{warehouseId(), skuId};
    }

    private InventoryOutboundFact outboundFact(Long wh, Long sku, String qty, long itemId) {
        return new InventoryOutboundFact(wh, sku, 0L, itemId, new BigDecimal(qty), null,
                OffsetDateTime.now(), "test:1");
    }

    @Test
    @DisplayName("出库扣减余额并写 SALES_OUT 流水，快照方向为减")
    void outboundDecrementsBalanceAndWritesReverseSnapshot() {
        Object[] s = stocked("ob1", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        BigDecimal before = balanceRow(wh, sku).getQuantity();
        assertThat(before).isEqualByComparingTo("10.0000");

        inventoryCommandService.postSalesOutbound(outboundFact(wh, sku, "3.0000", 900001L));

        InventoryBalanceEntity after = balanceRow(wh, sku);
        assertThat(after.getQuantity()).isEqualByComparingTo("7.0000");

        List<java.util.Map<String, Object>> salesOut = movementsOf(wh, sku).stream()
                .filter(m -> "SALES_OUT".equals(String.valueOf(m.get("movement_type"))))
                .toList();
        assertThat(salesOut).hasSize(1);
        java.util.Map<String, Object> row = salesOut.getFirst();
        // 方向感知快照：出库是减，这正是 V25 必须重建 ck_inventory_movement_snap 的原因
        assertThat(new BigDecimal(String.valueOf(row.get("before_quantity")))).isEqualByComparingTo("10.0000");
        assertThat(new BigDecimal(String.valueOf(row.get("quantity")))).isEqualByComparingTo("3.0000");
        assertThat(new BigDecimal(String.valueOf(row.get("after_quantity")))).isEqualByComparingTo("7.0000");
        // 单位以余额记账单位为准（Q13），由服务端取，不由调用方传
        assertThat(String.valueOf(row.get("unit_snapshot"))).isEqualTo(balanceRow(wh, sku).getUnit());
    }

    @Test
    @DisplayName("出库超过现有量被拒（41011），余额不变 —— 不引入负库存")
    void outboundBeyondOnHandIsRejected() {
        Object[] s = stocked("ob2", "5.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        expectCode(() -> inventoryCommandService.postSalesOutbound(outboundFact(wh, sku, "6.0000", 900002L)), 41011);
        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("5.0000");
    }

    @Test
    @DisplayName("从未入库的 (仓库, SKU) 出库被拒（41011），且不会建出零余额行")
    void outboundWithoutAnyBalanceIsRejectedWithoutCreatingRow() {
        Long skuId = newSkuOfType("ob3", "NON_STANDARD", "ON_SHELF");
        Long wh = warehouseId();
        assertThat(balanceRowCount(wh, skuId)).isZero();

        expectCode(() -> inventoryCommandService.postSalesOutbound(outboundFact(wh, skuId, "1.0000", 900003L)), 41011);
        // 出库不建行：没有余额行 = 从未入库 = 无货可出
        assertThat(balanceRowCount(wh, skuId)).isZero();
    }

    @Test
    @DisplayName("同一来源行重复出库被拒（41015），不会扣两次")
    void duplicateOutboundFromSameSourceLineIsRejected() {
        Object[] s = stocked("ob4", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        inventoryCommandService.postSalesOutbound(outboundFact(wh, sku, "2.0000", 900004L));
        expectCode(() -> inventoryCommandService.postSalesOutbound(outboundFact(wh, sku, "2.0000", 900004L)), 41015);
        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("8.0000");
    }

    @Test
    @DisplayName("预留占用可用量但不改物理库存；释放后可用量恢复")
    void reservationHoldsAvailableWithoutTouchingOnHand() {
        Object[] s = stocked("rs1", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        Long reservationId = reservations.reserve(new ReserveInventoryFact(
                wh, sku, "SALES_ORDER_ITEM", 700001L, 800001L,
                new BigDecimal("4.0000"), OffsetDateTime.now(), null));

        InventoryBalanceEntity held = balanceRow(wh, sku);
        assertThat(held.getQuantity()).as("预留不改物理库存").isEqualByComparingTo("10.0000");
        assertThat(held.getReservedQuantity()).isEqualByComparingTo("4.0000");

        reservations.release(reservationId);

        InventoryBalanceEntity released = balanceRow(wh, sku);
        assertThat(released.getReservedQuantity()).isEqualByComparingTo("0.0000");
        assertThat(released.getQuantity()).isEqualByComparingTo("10.0000");
    }

    @Test
    @DisplayName("预留超过可用量被拒（41011）")
    void reservationBeyondAvailableIsRejected() {
        Object[] s = stocked("rs2", "5.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        expectCode(() -> reservations.reserve(new ReserveInventoryFact(
                wh, sku, "SALES_ORDER_ITEM", 700002L, 800002L,
                new BigDecimal("6.0000"), OffsetDateTime.now(), null)), 41011);
        assertThat(balanceRow(wh, sku).getReservedQuantity()).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("出库不得吃掉已预留的货（41011）—— 可用量 = 现有量 − 预留量")
    void outboundCannotConsumeReservedStock() {
        Object[] s = stocked("rs3", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        reservations.reserve(new ReserveInventoryFact(
                wh, sku, "SALES_ORDER_ITEM", 700003L, 800003L,
                new BigDecimal("8.0000"), OffsetDateTime.now(), null));

        // 现有量 10，其中 8 已预留 → 可用量只有 2
        expectCode(() -> inventoryCommandService.postSalesOutbound(outboundFact(wh, sku, "3.0000", 900005L)), 41011);
        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("10.0000");
        assertThat(balanceRow(wh, sku).getReservedQuantity()).isEqualByComparingTo("8.0000");

        // 出满可用量是允许的
        inventoryCommandService.postSalesOutbound(outboundFact(wh, sku, "2.0000", 900006L));
        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("8.0000");
    }

    @Test
    @DisplayName("重复释放被拒（41016），可用量不会被虚增")
    void doubleReleaseIsRejected() {
        Object[] s = stocked("rs4", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        Long id = reservations.reserve(new ReserveInventoryFact(
                wh, sku, "SALES_ORDER_ITEM", 700004L, 800004L,
                new BigDecimal("3.0000"), OffsetDateTime.now(), null));
        reservations.release(id);
        expectCode(() -> reservations.release(id), 41016);
        assertThat(balanceRow(wh, sku).getReservedQuantity()).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("Q7：出库流水同样不可改删（新增类型不是绕过 append-only 的口子）")
    void outboundMovementsAreStillAppendOnly() {
        Object[] s = stocked("ob5", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];
        inventoryCommandService.postSalesOutbound(outboundFact(wh, sku, "1.0000", 900007L));

        Long movementId = ((Number) movementsOf(wh, sku).stream()
                .filter(m -> "SALES_OUT".equals(String.valueOf(m.get("movement_type"))))
                .findFirst().orElseThrow().get("id")).longValue();

        expectSqlFailure("UPDATE inventory_movement SET deleted = TRUE WHERE id = ?", movementId);
        expectSqlFailure("UPDATE inventory_movement SET quantity = 2, after_quantity = 8 WHERE id = ?", movementId);
        expectSqlFailure("DELETE FROM inventory_movement WHERE id = ?", movementId);
    }
}
