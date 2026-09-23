package net.lab1024.sa.admin.module.scm.delivery;

import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryOrdersForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryPrintOrdersForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryRouteForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryVersionForm;
import net.lab1024.sa.admin.module.scm.delivery.service.DeliveryRouteQueryService;
import net.lab1024.sa.admin.module.scm.delivery.service.DeliveryRouteService;
import net.lab1024.sa.admin.module.scm.warehouse.domain.form.WarehouseAddForm;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 5 §8.2：打印计次的**真并发**验收。
 *
 * <p><b>为什么必须关掉测试事务</b>：同一条线路上的两个打印请求要在两个**互相独立的事务**里跑；
 * 塞进同一个测试事务时它们共享同一条连接、互相看不见对方，线路聚合锁与
 * {@code print_count = print_count + 1} 的累加根本压不到，丢更新也就无法显形。
 * 因此本类用 {@code Propagation.NOT_SUPPORTED}，让每次 Service 调用自开事务
 * （代价是造数提交到测试库，随机前缀隔离）。
 *
 * <p>两个用例分别锁住两条不变量：不同幂等键并发重打同一订单必须<b>精确 +2</b>
 * （线路锁把写串行化，不产生 Lost Update 也不死锁）；同一幂等键并发提交必须
 * <b>只计一次</b>（后到者等先到事务出结果后走重放，而不是各计一次）。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("W5 并发打印累加（PG IT，无外层事务）")
class DeliveryPrintConcurrencyIT extends ScmW5PgITBase {
    /**
     * 并发等待上限：线路行锁的等待远小于它，超过即说明锁序有问题（死锁 / 活锁）。
     */
    private static final long TIMEOUT_SECONDS = 60;

    @Autowired
    DeliveryRouteService delivery;
    @Autowired
    DeliveryRouteQueryService deliveryQuery;

    /**
     * 本类新建并提交到测试库的仓库，用例结束后停用。
     *
     * <p><b>为什么必须停用</b>：{@code NOT_SUPPORTED} 让造数随各自事务提交，随机前缀能隔离业务数据，
     * 但 {@code warehouse.status = ENABLED} 是**全局**判据 —— 库存 IT 按「唯一启用仓库」推导默认仓库，
     * 残留一个启用仓库就会让它们整批报「当前启用仓库不是唯一一个」。停用而非删行，
     * 是为了不给 {@code delivery_route.warehouse_id} 留下悬空引用。
     */
    private final List<Long> committedWarehouseIds = new ArrayList<>();

    @Override
    protected void evictMybatisCache() {
        // 无外层事务 → 每次 DAO 调用都是新 session → 一级缓存天然为空
    }

    @AfterEach
    void disableCommittedWarehouses() {
        committedWarehouseIds.forEach(this::disableWarehouse);
        committedWarehouseIds.clear();
    }

    @Test
    @DisplayName("不同幂等键并发重打同一订单：两次都成功且 print_count 精确 +2")
    void concurrentReprintWithDifferentKeysIncrementsExactlyTwice() throws Exception {
        var ctx = plannedRoute();
        var outcomes = runConcurrently(
                () -> delivery.printOrders(ctx.route(), ordersForm(ctx.route(), ctx.a()), "w5c:diff-1"),
                () -> delivery.printOrders(ctx.route(), ordersForm(ctx.route(), ctx.a()), "w5c:diff-2"));
        assertThat(outcomes).allSatisfy(o -> assertThat(o.error()).isNull());
        assertThat(printCount(ctx.a())).isEqualTo(2);
        // 只卷入选定订单：同线路同客户的另一订单不受并发影响
        assertThat(printCount(ctx.b())).isZero();
        // 两次各留一条已完成记录，没有卡在半途的 claim
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_record WHERE result_data IS NULL "
                + "AND idempotency_key LIKE 'w5c:%'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("同一幂等键并发重打：只计一次，后到者得到同一份重放结果")
    void concurrentReplayWithSameKeyCountsOnce() throws Exception {
        var ctx = plannedRoute();
        // 两个线程各自写入结果，必须用线程安全容器，否则会丢元素
        var results = new java.util.concurrent.CopyOnWriteArrayList<Object>();
        var outcomes = runConcurrently(
                () -> results.add(delivery.printOrders(ctx.route(), ordersForm(ctx.route(), ctx.a()), "w5c:same-key")),
                () -> results.add(delivery.printOrders(ctx.route(), ordersForm(ctx.route(), ctx.a()), "w5c:same-key")));
        assertThat(outcomes).allSatisfy(o -> assertThat(o.error()).isNull());
        assertThat(printCount(ctx.a())).isEqualTo(1);
        // 重放方拿到的必须是同一份结果，而不是「成功但没打印」的空响应
        assertThat(results).hasSize(2).allSatisfy(result -> assertThat(result)
                .isInstanceOfSatisfying(net.lab1024.sa.admin.module.scm.delivery.domain.vo.DeliveryPrintResultVO.class,
                        vo -> assertThat(vo.getOrderCount()).isEqualTo(1)));
    }

    // ---- fixtures ----

    private record Ctx(Long route, Long sku, Long customer, Long a, Long b) {
    }

    private Ctx plannedRoute() {
        Long sku = newOnShelfSku("PRC");
        Long customer = newCustomer();
        // 停靠点坐标来自订单冻结地址，缺失时按「客户地址 == 冻结地址」回落客户坐标，
        // 故必须在下单前把地址写成基类 fixture 的冻结地址（'W5 IT 地址'），否则坐标为空、规划失败。
        jdbc.update("UPDATE customer SET address='W5 IT 地址',longitude=113.94,latitude=22.54,geom_crs='GCJ02' WHERE id=?", customer);
        Long a = confirmedSalesOrder(customer, sku, "1.0000", "1.0000");
        Long b = confirmedSalesOrder(customer, sku, "2.0000", "2.0000");
        Long route = plannedRoute(List.of(a, b));
        return new Ctx(route, sku, customer, a, b);
    }

    private Long plannedRoute(List<Long> orderIds) {
        Long route = route();
        add(route, orderIds);
        var form = new DeliveryVersionForm();
        form.setVersion(currentVersion(route));
        form.setReason("规划");
        delivery.plan(route, form);
        return route;
    }

    private Long route() {
        var warehouse = new WarehouseAddForm();
        warehouse.setWarehouseCode(prefix + "-" + UUID.randomUUID().toString().substring(0, 8));
        warehouse.setName("并发打印仓");
        warehouse.setLongitude(new BigDecimal("113.9"));
        warehouse.setLatitude(new BigDecimal("22.5"));
        warehouse.setGeomCrs("GCJ02");
        var form = new DeliveryRouteForm();
        form.setRouteName("并发打印线路");
        form.setDeliveryDate(LocalDate.now());
        form.setWarehouseId(createPrintWarehouse());
        return delivery.create(form);
    }

    private Long createPrintWarehouse() {
        var warehouse = new WarehouseAddForm();
        warehouse.setWarehouseCode(prefix + "-" + UUID.randomUUID().toString().substring(0, 8));
        warehouse.setName("并发打印仓");
        warehouse.setLongitude(new BigDecimal("113.9"));
        warehouse.setLatitude(new BigDecimal("22.5"));
        warehouse.setGeomCrs("GCJ02");
        Long id = warehouseService.create(warehouse);
        committedWarehouseIds.add(id);
        return id;
    }

    private void add(Long route, List<Long> orderIds) {
        var form = new DeliveryOrdersForm();
        form.setVersion(currentVersion(route));
        form.setOrderIds(orderIds);
        form.setReason("组单");
        delivery.addOrders(route, form);
    }

    private int currentVersion(Long route) {
        return deliveryQuery.detail(route).getRoute().getVersion();
    }

    private DeliveryPrintOrdersForm ordersForm(Long route, Long orderId) {
        var form = new DeliveryPrintOrdersForm();
        // 两个线程读取同一个版本：打印不推进线路版本，只有锁把两次累加串行化。
        form.setVersion(currentVersion(route));
        form.setOrderIds(List.of(orderId));
        return form;
    }

    private int printCount(Long orderId) {
        return jdbc.queryForObject("SELECT print_count FROM delivery_route_order WHERE order_id=?"
                        + " AND assignment_status='ACTIVE' AND deleted=FALSE", Integer.class, orderId);
    }

    /**
     * 子线程没有请求上下文，必须自带身份（{@code ScmOperator.current()} 与幂等 scope 都依赖它）。
     */
    private static void setThreadOperator() {
        var employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setActualName("W5 concurrent IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        SmartRequestUtil.setRequestUser(employee);
    }

    private record Outcome(Throwable error) {
    }

    /**
     * 用 {@code CountDownLatch} 对齐起跑线，而不是「先跑完第一个再跑第二个」：
     * 后者只能测到串行路径，测不到竞态。任一线程异常都收集回主线程断言，不静默吞掉。
     */
    private List<Outcome> runConcurrently(Runnable first, Runnable second) throws Exception {
        var start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Outcome>> futures = List.of(submit(pool, start, first), submit(pool, start, second));
            start.countDown();
            var outcomes = new ArrayList<Outcome>(2);
            for (Future<Outcome> future : futures) outcomes.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private Future<Outcome> submit(ExecutorService pool, CountDownLatch start, Runnable action) {
        return pool.submit(() -> {
            setThreadOperator();
            try {
                start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                action.run();
                return new Outcome(null);
            } catch (Throwable e) {
                return new Outcome(e);
            } finally {
                SmartRequestUtil.remove();
            }
        });
    }
}
