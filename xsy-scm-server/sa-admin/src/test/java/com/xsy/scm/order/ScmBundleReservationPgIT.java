package com.xsy.scm.order;

import com.xsy.scm.common.ScmW3PgITBase;
import com.xsy.scm.inventory.service.InventoryReservationService;
import com.xsy.scm.order.domain.entity.OrderBundleComponentEntity;
import com.xsy.scm.order.domain.entity.SalesOrderItemEntity;
import com.xsy.scm.order.manager.OrderBundleReservationPlanner;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 隔离 PG 中真实落库的组合组成 SKU 库存预留。
 * 测试库存余额仅作为夹具创建，预留动作必须走正式库存服务与 DAO。
 */
class ScmBundleReservationPgIT extends ScmW3PgITBase {

    @Autowired
    private InventoryReservationService reservationService;

    @Test
    void explicitBundleReservationConsumesComponentStockButNeverBundleSku() {
        Long warehouseId = jdbc.queryForObject(
                "SELECT id FROM xsy_warehouse WHERE deleted=FALSE ORDER BY id LIMIT 1", Long.class);
        Long componentSku = newOnShelfSku("RESV-COMP");
        Long bundleSku = newOnShelfSku("RESV-BUNDLE");
        Long balanceId = jdbc.queryForObject(
                "INSERT INTO xsy_inventory_balance (warehouse_id, sku_id, unit, quantity, reserved_quantity,"
                + " avg_cost) VALUES (?,?, 'kg', 30.0000, 0.0000, 5.0000) RETURNING id",
                Long.class, warehouseId, componentSku);

        SalesOrderItemEntity parent = new SalesOrderItemEntity();
        parent.setId(99881L);
        parent.setSkuId(bundleSku);
        parent.setActualQuantity(new BigDecimal("2.0000"));
        OrderBundleComponentEntity frozen = new OrderBundleComponentEntity();
        frozen.setId(99882L);
        frozen.setOrderItemId(parent.getId());
        frozen.setBundleSkuId(bundleSku);
        frozen.setComponentSkuId(componentSku);
        frozen.setComponentSaleQuantitySnapshot(new BigDecimal("4.0000"));
        frozen.setQuantityPerBundleSaleUnit(new BigDecimal("2.0000"));
        frozen.setComponentStockUnitSnapshot("kg");
        frozen.setStockUnitsPerComponentSaleUnit(new BigDecimal("5.0000"));

        var lines = OrderBundleReservationPlanner.lines(List.of(frozen), List.of(parent));
        reservationService.reserveForSalesOrder(warehouseId, 99880L, lines, OffsetDateTime.now());

        assertThat(jdbc.queryForObject(
                "SELECT reserved_quantity FROM xsy_inventory_balance WHERE id=?",
                BigDecimal.class, balanceId)).isEqualByComparingTo("20.0000");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM xsy_inventory_reservation "
                + "WHERE source_document_type='ORDER_BUNDLE_COMPONENT' AND source_document_id=99880 "
                + "AND source_document_item_id=99882 AND sku_id=? AND quantity=20.0000",
                Integer.class, componentSku)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM xsy_inventory_reservation WHERE sku_id=?",
                Integer.class, bundleSku)).isZero();
    }
}
