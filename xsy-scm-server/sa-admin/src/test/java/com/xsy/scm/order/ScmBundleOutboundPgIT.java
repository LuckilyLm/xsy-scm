package com.xsy.scm.order;

import com.xsy.scm.common.ScmW3PgITBase;
import com.xsy.scm.inventory.service.InventoryFulfillmentService;
import com.xsy.scm.inventory.service.InventoryReservationService;
import com.xsy.scm.inventory.service.InventorySalesReturnQueryService;
import com.xsy.scm.order.domain.entity.OrderBundleComponentEntity;
import com.xsy.scm.order.domain.entity.SalesOrderItemEntity;
import com.xsy.scm.order.manager.OrderBundleReservationPlanner;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** 组合商品显式预留和发车出库复用正式库存账本，不产生套餐 SKU 的库存流水。 */
class ScmBundleOutboundPgIT extends ScmW3PgITBase {

    @Autowired
    private InventoryReservationService reservationService;

    @Autowired
    private InventoryFulfillmentService fulfillmentService;

    @Autowired
    private InventorySalesReturnQueryService returnQueryService;

    @Test
    void dispatchConsumesTwoFrozenComponentsAndLeavesBundleSkuUntouched() {
        // 出库仓库必须在授权范围内：测试使用正式数据范围允许的管理员身份。
        ((RequestEmployee) SmartRequestUtil.getRequestUser()).setAdministratorFlag(true);
        Long warehouse = jdbc.queryForObject(
                "SELECT id FROM xsy_warehouse WHERE deleted=FALSE ORDER BY id LIMIT 1", Long.class);
        Long bundleSku = newOnShelfSku("OUT-BUNDLE");
        Long firstSku = newOnShelfSku("OUT-A");
        Long secondSku = newOnShelfSku("OUT-B");
        long orderId = 998801L;
        long orderItemId = 998802L;
        long firstComponentId = 998803L;
        long secondComponentId = 998804L;
        long routeId = 998805L;

        Long firstBalanceId = seedBalance(warehouse, firstSku, "50.0000", "5.0000");
        Long secondBalanceId = seedBalance(warehouse, secondSku, "40.0000", "8.0000");
        SalesOrderItemEntity parent = new SalesOrderItemEntity();
        parent.setId(orderItemId);
        parent.setSkuId(bundleSku);
        parent.setActualQuantity(new BigDecimal("2.0000"));
        var first = component(orderItemId, bundleSku, firstComponentId, firstSku, "2.0000", "5.0000");
        var second = component(orderItemId, bundleSku, secondComponentId, secondSku, "3.0000", "2.0000");
        var components = List.of(first, second);

        reservationService.reserveForSalesOrder(warehouse, orderId,
                OrderBundleReservationPlanner.lines(components, List.of(parent)), OffsetDateTime.now());
        assertThat(reserved(firstBalanceId)).isEqualByComparingTo("20.0000");
        assertThat(reserved(secondBalanceId)).isEqualByComparingTo("12.0000");

        var dispatched = fulfillmentService.dispatchOutbound(new InventoryFulfillmentService.Command(
                routeId, warehouse, OffsetDateTime.now(), "bundle-it",
                List.of(
                        new InventoryFulfillmentService.Line(orderId, orderItemId, firstSku,
                                new BigDecimal("4.0000"), "kg", new BigDecimal("5.0000"),
                                firstComponentId, bundleSku, new BigDecimal("2.0000")),
                        new InventoryFulfillmentService.Line(orderId, orderItemId, secondSku,
                                new BigDecimal("6.0000"), "kg", new BigDecimal("2.0000"),
                                secondComponentId, bundleSku, new BigDecimal("2.0000")))));

        assertThat(dispatched.outboundId()).isNotNull();
        assertThat(dispatched.shippedLineCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT quantity FROM xsy_inventory_balance WHERE id=?", BigDecimal.class, firstBalanceId))
                .isEqualByComparingTo("30.0000");
        assertThat(jdbc.queryForObject(
                "SELECT quantity FROM xsy_inventory_balance WHERE id=?", BigDecimal.class, secondBalanceId))
                .isEqualByComparingTo("28.0000");
        assertThat(reserved(firstBalanceId)).isEqualByComparingTo("0.0000");
        assertThat(reserved(secondBalanceId)).isEqualByComparingTo("0.0000");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM xsy_inventory_reservation WHERE source_document_id=? "
                + "AND source_document_type='ORDER_BUNDLE_COMPONENT' AND status='CONSUMED'",
                Integer.class, orderId)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM xsy_inventory_movement "
                + "WHERE movement_type='SALES_OUT' AND sku_id=?",
                Integer.class, bundleSku)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM xsy_inventory_outbound_item "
                + "WHERE outbound_id=? AND bundle_component_id IS NOT NULL",
                Integer.class, dispatched.outboundId())).isEqualTo(2);

        var movements = returnQueryService.bundleSalesOutAllocations(orderItemId);
        assertThat(movements).hasSize(2);
        assertThat(movements).extracting(item -> item.getBundleComponentId())
                .containsExactlyInAnyOrder(firstComponentId, secondComponentId);
        assertThat(movements).allSatisfy(item -> assertThat(item.getUnitCost()).isNotNull());
    }

    private Long seedBalance(Long warehouse, Long sku, String quantity, String cost) {
        return jdbc.queryForObject(
                "INSERT INTO xsy_inventory_balance (warehouse_id, sku_id, unit, quantity, reserved_quantity, avg_cost)"
                + " VALUES (?,?, 'kg', ?::numeric, 0.0000, ?::numeric) RETURNING id",
                Long.class, warehouse, sku, quantity, cost);
    }

    private BigDecimal reserved(Long balanceId) {
        return jdbc.queryForObject("SELECT reserved_quantity FROM xsy_inventory_balance WHERE id=?",
                BigDecimal.class, balanceId);
    }

    private static OrderBundleComponentEntity component(Long orderItemId, Long bundleSku, Long componentId,
            Long skuId, String unitsPerBundle, String stockFactor) {
        var component = new OrderBundleComponentEntity();
        component.setId(componentId);
        component.setOrderItemId(orderItemId);
        component.setBundleSkuId(bundleSku);
        component.setComponentSkuId(skuId);
        component.setComponentSaleQuantitySnapshot(new BigDecimal("2.0000")
                .multiply(new BigDecimal(unitsPerBundle)));
        component.setQuantityPerBundleSaleUnit(new BigDecimal(unitsPerBundle));
        component.setComponentStockUnitSnapshot("kg");
        component.setStockUnitsPerComponentSaleUnit(new BigDecimal(stockFactor));
        return component;
    }
}
