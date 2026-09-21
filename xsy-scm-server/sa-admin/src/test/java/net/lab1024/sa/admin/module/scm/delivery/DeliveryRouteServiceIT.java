package net.lab1024.sa.admin.module.scm.delivery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.delivery.service.*;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.*;
import net.lab1024.sa.admin.module.scm.delivery.domain.vo.*;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderCancelForm;
import net.lab1024.sa.admin.module.scm.warehouse.domain.form.WarehouseAddForm;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Small PostgreSQL smoke suite; use an isolated DB on local Docker Desktop. No external map calls.
 */
class DeliveryRouteServiceIT extends ScmW5PgITBase {
    @Autowired
    DeliveryRouteService delivery;
    @Autowired
    DeliveryRouteQueryService deliveryQuery;
    @Autowired
    DeliveryCandidateOrderQueryService candidates;

    @Test
    void groupReorderFreezePrintAndRelease() throws Exception {
        Long sku = newOnShelfSku("DL");
        Long c1 = newCustomer();
        Long c2 = newCustomer();
        // The fixture order address is identical; coordinates must be frozen at order creation.
        jdbc.update("UPDATE customer SET address='W5 IT 地址',longitude=113.94,latitude=22.54,geom_crs='GCJ02' WHERE id IN (?,?)", c1, c2);
        Long a = confirmedSalesOrder(c1, sku, "1.0000", "1.0000");
        Long b = confirmedSalesOrder(c1, sku, "2.0000", "2.0000");
        Long c = confirmedSalesOrder(c2, sku, "3.0000", "3.0000");
        Long id = route();
        add(id, List.of(a, b, c));
        var detail = deliveryQuery.detail(id);
        assertThat(detail.getStops()).hasSize(2);
        assertThat(detail.getOrders()).hasSize(3);
        assertThat(detail.getRoute().getLocatedCount()).isEqualTo(2);
        assertThat(detail.getStops().getFirst().getLongitude()).isEqualByComparingTo("113.94");
        assertThat(json.writeValueAsString(detail)).contains("113.94");
        var filter = new DeliveryQueryForm();
        filter.setCustomerId(c1);
        assertThat(candidates.query(filter).getList()).isEmpty();
        Long other = route();
        expectCode(() -> add(other, List.of(a)), 41103);
        var stale = new DeliveryReorderForm();
        stale.setVersion(0);
        stale.setStopIds(detail.getStops().stream().map(DeliveryStopVO::getId).toList());
        expectCode(() -> delivery.reorder(id, stale), 40921);
        var reorder = new DeliveryReorderForm();
        reorder.setVersion(detail.getRoute().getVersion());
        var reversed = detail.getStops().reversed().stream().map(DeliveryStopVO::getId).toList();
        reorder.setStopIds(reversed);
        delivery.reorder(id, reorder);
        assertThat(deliveryQuery.detail(id).getStops().stream().map(DeliveryStopVO::getId).toList()).isEqualTo(reversed);
        delivery.plan(id, version(id));
        expectCode(() -> add(id, List.of(a)), 41101);
        var cancelOrder = new OrderCancelForm();
        cancelOrder.setOrderId(a);
        cancelOrder.setVersion(salesOrderService.lock(a).getVersion());
        cancelOrder.setReason("验证占用保护");
        // Existing order state machine already rejects cancellation of CONFIRMED orders; preserve its error code.
        expectCode(() -> salesOrderService.cancel(cancelOrder, prefix + ":cancel-assigned"), 40960);
        jdbc.update("UPDATE customer SET address='新地址',longitude=120,latitude=30 WHERE id=?", c1);
        assertThat(deliveryQuery.detail(id).getStops()).allSatisfy(s -> assertThat(s.getAddressSnapshot()).isEqualTo("W5 IT 地址"));
        assertThat(deliveryQuery.print(id).getItems()).hasSize(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_movement WHERE sku_id=?", Integer.class, sku)).isZero();
        var cancel = version(id);
        cancel.setReason("调整配送计划");
        delivery.cancel(id, cancel);
        assertThat(deliveryQuery.detail(id).getOrders()).hasSize(3); // cancelled history is retained
        assertThat(candidates.query(filter).getList()).hasSize(2);
        add(other, List.of(a));
        assertThat(deliveryQuery.detail(other).getOrders()).hasSize(1);
    }

    @Test
    void requireCompleteSameCrsLocationsAndRemoveEmptyStop() {
        Long sku = newOnShelfSku("GUARD");
        Long c = newCustomer();
        Long a = confirmedSalesOrder(c, sku, "1.0000", "1.0000");
        Long id = route();
        add(id, List.of(a));
        var detail = deliveryQuery.detail(id);
        Long stop = detail.getStops().getFirst().getId();
        expectCode(() -> delivery.plan(id, version(id)), 41104);
        expectSqlFailure("UPDATE delivery_route_stop SET longitude=113.94 WHERE id=?", stop);
        var location = new DeliveryStopForm();
        location.setVersion(version(id).getVersion());
        location.setLongitude(new BigDecimal("113.94"));
        location.setLatitude(new BigDecimal("22.54"));
        location.setGeomCrs("WGS84");
        delivery.locate(id, stop, location);
        expectCode(() -> delivery.plan(id, version(id)), 41104);
        location.setVersion(version(id).getVersion());
        location.setGeomCrs("GCJ02");
        delivery.locate(id, stop, location);
        var remove = version(id);
        remove.setReason("调整线路");
        delivery.removeOrder(id, a, remove);
        assertThat(deliveryQuery.detail(id).getStops()).isEmpty();
        assertThat(deliveryQuery.detail(id).getOrders()).isEmpty();
        add(id, List.of(a));
        assertThat(deliveryQuery.detail(id).getStops().getFirst().getStopSeq()).isEqualTo(1);
        var query = new DeliveryQueryForm();
        query.setDeliveryDate(LocalDate.now());
        assertThat(deliveryQuery.query(query).getList()).isNotEmpty();
    }

    private Long route() {
        var w = new WarehouseAddForm();
        w.setWarehouseCode(prefix + "-" + java.util.UUID.randomUUID().toString().substring(0, 8));
        w.setName("配送仓");
        w.setLongitude(new BigDecimal("113.9"));
        w.setLatitude(new BigDecimal("22.5"));
        w.setGeomCrs("GCJ02");
        var f = new DeliveryRouteForm();
        f.setRouteName("城区配送");
        f.setDeliveryDate(LocalDate.now());
        f.setWarehouseId(warehouseService.create(w));
        return delivery.create(f);
    }

    private DeliveryVersionForm version(Long id) {
        var f = new DeliveryVersionForm();
        f.setVersion(deliveryQuery.detail(id).getRoute().getVersion());
        return f;
    }

    private void add(Long id, List<Long> ids) {
        var f = new DeliveryOrdersForm();
        f.setVersion(version(id).getVersion());
        f.setOrderIds(ids);
        f.setReason("配送组单");
        delivery.addOrders(id, f);
    }
}
