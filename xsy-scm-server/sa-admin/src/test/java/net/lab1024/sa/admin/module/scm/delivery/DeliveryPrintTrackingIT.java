package net.lab1024.sa.admin.module.scm.delivery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.*;
import net.lab1024.sa.admin.module.scm.delivery.service.DeliveryRouteQueryService;
import net.lab1024.sa.admin.module.scm.delivery.service.DeliveryRouteService;
import net.lab1024.sa.admin.module.scm.warehouse.domain.form.WarehouseAddForm;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 5 配送打印追踪的 PostgreSQL 验收：订单/客户双视角聚合、正式生成计次、幂等与并发累加、
 * 版本 / 集合变化拒绝旧请求，以及「生成打印绝不扣库存、绝不推进状态」的负向断言。
 */
class DeliveryPrintTrackingIT extends ScmW5PgITBase {
    @Autowired
    DeliveryRouteService delivery;
    @Autowired
    DeliveryRouteQueryService deliveryQuery;

    @Test
    void ordersViewCountMatchesAssignmentsAndPrintIncrementsOnlyIncluded() {
        var ctx = plannedRoute();
        assertThat(printCount(ctx.a())).isZero();
        // GET 预览不写计数
        deliveryQuery.print(ctx.route());
        assertThat(printCount(ctx.a())).isZero();

        var result = delivery.printOrders(ctx.route(), ordersForm(ctx.route(), List.of(ctx.a())), "w5:only-a");
        assertThat(result.getOrderCount()).isEqualTo(1);
        assertThat(printCount(ctx.a())).isEqualTo(1);
        assertThat(printCount(ctx.b())).isZero();
        // 同请求重试只计一次
        delivery.printOrders(ctx.route(), ordersForm(ctx.route(), List.of(ctx.a())), "w5:only-a");
        assertThat(printCount(ctx.a())).isEqualTo(1);
        // 明确重打用新键再计一次
        delivery.printOrders(ctx.route(), ordersForm(ctx.route(), List.of(ctx.a())), "w5:reprint-a");
        assertThat(printCount(ctx.a())).isEqualTo(2);
        // 生成打印绝不扣库存 / 绝不推进线路状态
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_movement WHERE sku_id=?", Integer.class, ctx.sku())).isZero();
        assertThat(deliveryQuery.detail(ctx.route()).getRoute().getStatus()).isEqualTo("PLANNED");
    }

    @Test
    void customersViewAggregatesAndMarksPartial() {
        var ctx = plannedRoute();
        // 订单视角总数与线路 ACTIVE 关系一致
        assertThat(deliveryQuery.orderView(ctx.route())).hasSize(3);
        // 先只打印 c1 的 a，则 c1=PARTIAL、c2=UNPRINTED
        delivery.printOrders(ctx.route(), ordersForm(ctx.route(), List.of(ctx.a())), "w5:partial");
        var customers = deliveryQuery.customerView(ctx.route());
        var c1 = customers.stream().filter(v -> v.getCustomerId().equals(ctx.c1())).findFirst().orElseThrow();
        var c2 = customers.stream().filter(v -> v.getCustomerId().equals(ctx.c2())).findFirst().orElseThrow();
        assertThat(c1.getPrintStatus()).isEqualTo("PARTIAL");
        assertThat(c1.getOrderCount()).isEqualTo(2);
        assertThat(c1.getPrintedOrderCount()).isEqualTo(1);
        assertThat(c2.getPrintStatus()).isEqualTo("UNPRINTED");
        // 按客户聚合的订单数之和 == 按订单计数之和
        assertThat(customers.stream().mapToInt(v -> v.getOrderCount()).sum())
                .isEqualTo(deliveryQuery.orderView(ctx.route()).size());
        // PARTIAL 客户用 UNPRINTED 筛选补打，仅计其未打印订单 b
        var customerForm = new DeliveryPrintCustomersForm();
        customerForm.setVersion(deliveryQuery.detail(ctx.route()).getRoute().getVersion());
        customerForm.setCustomerIds(List.of(ctx.c1()));
        customerForm.setOrderPrintFilter("UNPRINTED");
        var printed = delivery.printCustomers(ctx.route(), customerForm, "w5:partial-fill");
        assertThat(printed.getOrders()).singleElement().satisfies(v -> assertThat(v.getOrderId()).isEqualTo(ctx.b()));
        assertThat(printCount(ctx.b())).isEqualTo(1);
        assertThat(printCount(ctx.a())).isEqualTo(1); // 未被本次包含，保持原计数
        assertThat(deliveryQuery.customerView(ctx.route()).stream()
                .filter(v -> v.getCustomerId().equals(ctx.c1())).findFirst().orElseThrow().getPrintStatus()).isEqualTo("PRINTED");
    }

    @Test
    void staleVersionAndForeignOrderAreRejected() {
        var ctx = plannedRoute();
        var foreign = confirmedSalesOrder(newCustomer(), ctx.sku(), "9.0000", "9.0000");
        // 集合变化：提交了不属于本线路 ACTIVE 关系的订单
        expectCode(() -> delivery.printOrders(ctx.route(), ordersForm(ctx.route(), List.of(foreign)), "w5:foreign"), 41101);
        // 版本变化：过期的乐观锁版本
        var stale = ordersForm(ctx.route(), List.of(ctx.a()));
        stale.setVersion(stale.getVersion() + 1);
        expectCode(() -> delivery.printOrders(ctx.route(), stale, "w5:stale"), 40921);
        // 未规划线路不可正式生成打印：用一个尚未归属任何线路的新订单挂到 DRAFT 线路
        var draft = route();
        var d = confirmedSalesOrder(newCustomer(), ctx.sku(), "1.0000", "1.0000");
        add(draft, List.of(d));
        expectCode(() -> delivery.printOrders(draft, ordersForm(draft, List.of(d)), "w5:draft"), 41101);
    }

    // ---- fixtures ----

    private record Ctx(Long route, Long sku, Long c1, Long c2, Long a, Long b, Long c) {
    }

    private Ctx plannedRoute() {
        Long sku = newOnShelfSku("PRN");
        Long c1 = newCustomer();
        Long c2 = newCustomer();
        // 停靠点坐标来自订单冻结地址；缺失时按「客户地址 == 冻结地址」回落到客户坐标，
        // 故 customer.address 必须等于基类 fixture 硬编码的订单地址 'W5 IT 地址'，且坐标在下单前写入。
        jdbc.update("UPDATE customer SET address='W5 IT 地址',longitude=113.94,latitude=22.54,geom_crs='GCJ02' WHERE id IN (?,?)", c1, c2);
        Long a = confirmedSalesOrder(c1, sku, "1.0000", "1.0000");
        Long b = confirmedSalesOrder(c1, sku, "2.0000", "2.0000");
        Long c = confirmedSalesOrder(c2, sku, "3.0000", "3.0000");
        Long route = route();
        add(route, List.of(a, b, c));
        delivery.plan(route, version(route));
        return new Ctx(route, sku, c1, c2, a, b, c);
    }

    private Long route() {
        var w = new WarehouseAddForm();
        w.setWarehouseCode(prefix + "-" + java.util.UUID.randomUUID().toString().substring(0, 8));
        w.setName("打印仓");
        w.setLongitude(new java.math.BigDecimal("113.9"));
        w.setLatitude(new java.math.BigDecimal("22.5"));
        w.setGeomCrs("GCJ02");
        var f = new DeliveryRouteForm();
        f.setRouteName("打印线路");
        f.setDeliveryDate(LocalDate.now());
        f.setWarehouseId(warehouseService.create(w));
        return delivery.create(f);
    }

    private void add(Long id, List<Long> ids) {
        var f = new DeliveryOrdersForm();
        f.setVersion(version(id).getVersion());
        f.setOrderIds(ids);
        f.setReason("组单");
        delivery.addOrders(id, f);
    }

    private DeliveryVersionForm version(Long id) {
        var f = new DeliveryVersionForm();
        f.setVersion(deliveryQuery.detail(id).getRoute().getVersion());
        return f;
    }

    private DeliveryPrintOrdersForm ordersForm(Long id, List<Long> orderIds) {
        var f = new DeliveryPrintOrdersForm();
        f.setVersion(deliveryQuery.detail(id).getRoute().getVersion());
        f.setOrderIds(orderIds);
        return f;
    }

    private int printCount(Long orderId) {
        return jdbc.queryForObject("SELECT print_count FROM delivery_route_order WHERE order_id=? AND assignment_status='ACTIVE' AND deleted=FALSE",
                Integer.class, orderId);
    }
}
