package net.lab1024.sa.admin.module.scm.delivery;

import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.*;
import net.lab1024.sa.admin.module.scm.delivery.service.DeliveryRouteQueryService;
import net.lab1024.sa.admin.module.scm.delivery.service.DeliveryRouteService;
import net.lab1024.sa.admin.module.scm.warehouse.domain.form.WarehouseAddForm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

/**
 * Wave 5 配送打印追踪的 PostgreSQL 验收：订单/客户双视角聚合、正式生成计次、幂等与并发累加、
 * 版本 / 集合变化拒绝旧请求，以及「生成打印绝不扣库存、绝不推进状态」的负向断言。
 *
 * <p>只读视图现在按司机维度收口并按权限抹金额，本类的调用者因此需要「全部范围 + 全部功能点」；
 * 范围与金额口径由 {@code ScmDeliveryDataScopePgIT} 专门覆盖。
 */
class DeliveryPrintTrackingIT extends ScmW5PgITBase {
    @Autowired
    DeliveryRouteService delivery;
    @Autowired
    DeliveryRouteQueryService deliveryQuery;

    private MockedStatic<StpUtil> permissions;

    @BeforeEach
    void grantEveryDeliveryPermission() {
        permissions = mockStatic(StpUtil.class);
        permissions.when(() -> StpUtil.hasPermission(anyString())).thenReturn(true);
    }

    @AfterEach
    void releasePermissions() {
        permissions.close();
    }

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

    @Test
    void customerStatusFilterUsesServerRecomputedStatus() {
        var ctx = plannedRoute();
        // 只打印 c1 的 a：c1=PARTIAL、c2=UNPRINTED
        delivery.printOrders(ctx.route(), ordersForm(ctx.route(), List.of(ctx.a())), "w5:status-start");
        // 两个客户都作为候选提交，服务端只保留当前确实 PARTIAL 的 c1，并按订单筛选补打 b
        var form = customersForm(ctx.route(), List.of(ctx.c1(), ctx.c2()), "PARTIAL", "UNPRINTED");
        var printed = delivery.printCustomers(ctx.route(), form, "w5:status-partial-fill");
        assertThat(printed.getOrders()).singleElement().satisfies(v -> assertThat(v.getOrderId()).isEqualTo(ctx.b()));
        assertThat(printCount(ctx.b())).isEqualTo(1);
        assertThat(printCount(ctx.c())).isZero(); // c2 全部未打印，不属于 PARTIAL
        assertThat(printCount(ctx.a())).isEqualTo(1); // 本次未选中，计数不变
    }

    @Test
    void clientClaimedUnprintedCustomerIsNotReprintedAfterItGotPrinted() {
        var ctx = plannedRoute();
        // 前端按「c1 未打印」选好客户，但生成前 c1 已被整批打印完
        delivery.printCustomers(ctx.route(), customersForm(ctx.route(), List.of(ctx.c1()), "ALL", "ALL"), "w5:stale-fill");
        assertThat(deliveryQuery.customerView(ctx.route()).stream()
                .filter(v -> v.getCustomerId().equals(ctx.c1())).findFirst().orElseThrow().getPrintStatus())
                .isEqualTo("PRINTED");
        // 仍按 UNPRINTED 提交：c1 被服务端排除，只剩 c2 → 展开只含 c
        var narrow = customersForm(ctx.route(), List.of(ctx.c1(), ctx.c2()), "UNPRINTED", "ALL");
        assertThat(delivery.printCustomers(ctx.route(), narrow, "w5:stale-narrow").getOrders())
                .singleElement().satisfies(v -> assertThat(v.getOrderId()).isEqualTo(ctx.c()));
        // 候选客户全部不匹配时展开为空：拒绝而不是返回「成功但零单」
        var empty = customersForm(ctx.route(), List.of(ctx.c1()), "UNPRINTED", "ALL");
        expectCode(() -> delivery.printCustomers(ctx.route(), empty, "w5:stale-empty"), 41101);
    }

    @Test
    void statusScopeWithoutCustomerListPrintsRouteAndAmbiguousRequestIsRejected() {
        var ctx = plannedRoute();
        // 无名单 + ALL 等于「无选择重打整条线路」，参数层直接拒绝
        var ambiguous = customersForm(ctx.route(), null, "ALL", "ALL");
        expectCode(() -> delivery.printCustomers(ctx.route(), ambiguous, "w5:scope-ambiguous"), 40000);
        // 收窄到 UNPRINTED 即可整线路补打：三张订单全部命中
        var scope = customersForm(ctx.route(), null, "UNPRINTED", "ALL");
        assertThat(delivery.printCustomers(ctx.route(), scope, "w5:scope-unprinted").getOrderCount()).isEqualTo(3);
        assertThat(printCount(ctx.a())).isEqualTo(1);
        assertThat(printCount(ctx.b())).isEqualTo(1);
        assertThat(printCount(ctx.c())).isEqualTo(1);
    }

    @Test
    void releasedOrdersAndZeroActiveCustomerStayOutOfCustomerStatus() {
        var ctx = draftRoute();
        // 规划前把 c2 的唯一订单移出线路：软删 + RELEASED 后 c2 无有效订单
        var remove = version(ctx.route());
        remove.setReason("移出");
        delivery.removeOrder(ctx.route(), ctx.c(), remove);
        delivery.plan(ctx.route(), version(ctx.route()));
        assertThat(deliveryQuery.customerView(ctx.route()))
                .singleElement().satisfies(v -> assertThat(v.getCustomerId()).isEqualTo(ctx.c1()));
        // 整线路 UNPRINTED 补打不会带上已移出的 c
        assertThat(delivery.printCustomers(ctx.route(), customersForm(ctx.route(), null, "UNPRINTED", "ALL"), "w5:released")
                .getOrders()).extracting("orderId").containsExactlyInAnyOrder(ctx.a(), ctx.b());
        // 已被移出（零有效订单）的客户不能生成虚假 UNPRINTED 客户
        var ghost = customersForm(ctx.route(), List.of(ctx.c2()), "UNPRINTED", "ALL");
        expectCode(() -> delivery.printCustomers(ctx.route(), ghost, "w5:released-ghost"), 41101);
    }

    // ---- fixtures ----

    private DeliveryPrintCustomersForm customersForm(Long id, List<Long> customerIds, String statusFilter, String orderFilter) {
        var form = new DeliveryPrintCustomersForm();
        form.setVersion(deliveryQuery.detail(id).getRoute().getVersion());
        form.setCustomerIds(customerIds);
        form.setCustomerStatusFilter(statusFilter);
        form.setOrderPrintFilter(orderFilter);
        return form;
    }

    private record Ctx(Long route, Long sku, Long c1, Long c2, Long a, Long b, Long c) {
    }

    private Ctx plannedRoute() {
        var ctx = draftRoute();
        delivery.plan(ctx.route(), version(ctx.route()));
        return ctx;
    }

    /** 同样三张订单的线路，但停在 DRAFT：用于验证移出订单后重新组单的聚合口径。 */
    private Ctx draftRoute() {
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
        // 组单的前置条件是分拣已完成（P1 裁决第 11 条与补充第 18 条）。
        sortingCompletedFor(ids.toArray(Long[]::new));
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
