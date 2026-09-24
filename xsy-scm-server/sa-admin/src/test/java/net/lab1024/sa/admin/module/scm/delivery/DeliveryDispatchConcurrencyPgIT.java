package net.lab1024.sa.admin.module.scm.delivery;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryOrdersForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryRouteForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryVersionForm;
import net.lab1024.sa.admin.module.scm.delivery.service.DeliveryRouteService;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingActionForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingEntryForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingEntryItemForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingTaskCreateForm;
import net.lab1024.sa.admin.module.scm.sorting.service.SortingTaskService;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 发车并发（PG IT，无外层事务）。
 *
 * <p>关外层事务的原因与 {@code ScmInventoryReservationConcurrencyIT} 相同：把多个线程塞进同一个
 * 测试事务里，它们其实共享同一条连接、互相排队，「并发」是假的。
 *
 * <p>两条不变量：
 * <ol>
 *   <li><b>两个线程同时发车同一条线路，只有一份库存事实</b>：一条线路一张出库单（部分唯一索引
 *       与线路行锁双层），每订单行一条 {@code SALES_OUT}，实发量只被扣一次。
 *       断言只钉「成功了几份 + 账对不对」，<b>不钉哪个线程输</b>，也不钉它输在哪条判据上
 *       （线路状态、乐观锁版本、唯一索引都可能先拒掉它）；</li>
 *   <li><b>发车与分拣重开互斥</b>：两者都要在订单行上排队。允许的结局只有
 *       「发车成功 + 重开被拒」与「重开成功 + 整条线路被拒发」两种；
 *       若允许两者都成功，就会留下一张已真实出库的订单行还能继续改分拣量。</li>
 * </ol>
 *
 * <p>本类刻意自带夹具而不与 {@code DeliveryDispatchPgIT} 共用：那边的数据在测试结束时回滚，
 * 这边每一步都是真提交，混在一起会让残留互相污染。
 */
@DisplayName("配送 L3 发车并发（PG IT）")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeliveryDispatchConcurrencyPgIT extends ScmW6PgITBase {

    private static final long TIMEOUT_SECONDS = 90;

    @Autowired
    private DeliveryRouteService routeService;

    @Autowired
    private SortingTaskService sorting;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<Long> createdWarehouses = new ArrayList<>();

    @Test
    @DisplayName("两个线程同时发车同一条线路：只生成一张出库单，库存只扣一次")
    void concurrentDispatchOfSameRouteWritesExactlyOneOutbound() throws Exception {
        Long warehouseId = locatedWarehouse("cd1");
        Long skuId = newOnShelfSku("cd1");
        stockIn(warehouseId, skuId, "cd1", "10.0000");
        Long first = sortedOrder("cd1a", skuId, warehouseId, "4.0000");
        Long second = sortedOrder("cd1b", skuId, warehouseId, "3.0000");
        Long routeId = plannedRoute(warehouseId, first, second);
        BigDecimal before = onHand(warehouseId, skuId);

        List<Outcome> outcomes = runConcurrently(List.of(
                () -> transactionTemplate.execute(status -> routeService.dispatch(routeId,
                        dispatchForm(routeId), key("race-a"))),
                () -> transactionTemplate.execute(status -> routeService.dispatch(routeId,
                        dispatchForm(routeId), key("race-b")))));

        long succeeded = outcomes.stream().filter(Outcome::ok).count();
        assertThat(succeeded).as("同一时刻只允许一个发车提交成功").isEqualTo(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.ok()).allSatisfy(outcome ->
                assertThat(outcome.error()).as("输的那笔必须是业务拒绝，不能是脚手架异常").isNotNull());

        assertThat(outboundCountForRoute(routeId)).as("一条线路一张出库单").isEqualTo(1);
        assertThat(movementCountForRoute(routeId)).as("两个订单行各一条 SALES_OUT，不多不少").isEqualTo(2);
        assertThat(onHand(warehouseId, skuId))
                .as("存量只按 4 + 3 扣一次").isEqualByComparingTo(before.subtract(new BigDecimal("7.0000")));
        assertThat(reservedNeverGoesAboveOnHand(warehouseId, skuId)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM delivery_route_order WHERE route_id = ? AND fulfillment_status = 'IN_TRANSIT'"
                        + " AND deleted = FALSE", Integer.class, routeId)).isEqualTo(2);
    }

    @Test
    @DisplayName("发车与分拣重开互斥：不允许出现「已真实出库还能改分拣量」")
    void dispatchAndReopenAreMutuallyExclusive() throws Exception {
        Long warehouseId = locatedWarehouse("cd2");
        Long skuId = newOnShelfSku("cd2");
        stockIn(warehouseId, skuId, "cd2", "10.0000");
        Long order = sortedOrder("cd2a", skuId, warehouseId, "5.0000");
        Long routeId = plannedRoute(warehouseId, order);
        Long taskId = taskOfOrderLine(order);

        List<Outcome> outcomes = runConcurrently(List.of(
                () -> transactionTemplate.execute(status -> routeService.dispatch(routeId,
                        dispatchForm(routeId), key("race-dispatch"))),
                () -> {
                    transactionTemplate.executeWithoutResult(status ->
                            sorting.reopen(taskId, reopenForm(taskId)));
                    return null;
                }));

        boolean dispatched = "DISPATCHED".equals(routeStatus(routeId));
        String taskStatus = jdbc.queryForObject("SELECT status FROM sorting_task WHERE id = ?", String.class, taskId);

        assertThat(outcomes.stream().filter(Outcome::ok).count())
                .as("两个动作必须有一个被拒：%s", describe(outcomes)).isEqualTo(1);
        if (dispatched) {
            assertThat(taskStatus).as("发车赢了，任务不能被重开").isEqualTo("COMPLETED");
            assertThat(outboundCountForRoute(routeId)).isEqualTo(1);
            assertThat(onHand(warehouseId, skuId)).isEqualByComparingTo("5.0000");
        } else {
            assertThat(taskStatus).as("重开赢了，整条线路不该发车").isEqualTo("SORTING");
            assertThat(routeStatus(routeId)).isEqualTo("PLANNED");
            assertThat(outboundCountForRoute(routeId)).as("没有出库单").isZero();
            assertThat(onHand(warehouseId, skuId)).as("库存一行都没动").isEqualByComparingTo("10.0000");
        }
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private record Outcome(boolean ok, Throwable error) {
    }

    private List<Outcome> runConcurrently(List<Callable<Object>> tasks) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<Object>> futures = new ArrayList<>(tasks.size());
            for (Callable<Object> task : tasks) {
                futures.add(pool.submit(() -> {
                    setThreadOperator();
                    start.await();
                    try {
                        task.call();
                        return new Outcome(true, null);
                    } catch (Throwable error) {
                        return new Outcome(false, error);
                    } finally {
                        SmartRequestUtil.remove();
                    }
                }));
            }
            start.countDown();
            List<Outcome> outcomes = new ArrayList<>(futures.size());
            for (Future<Object> future : futures) {
                try {
                    outcomes.add((Outcome) future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
                } catch (ExecutionException | TimeoutException e) {
                    outcomes.add(new Outcome(false, e.getCause() == null ? e : e.getCause()));
                }
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private static String describe(List<Outcome> outcomes) {
        return outcomes.stream().map(o -> o.ok() ? "OK" : o.error().getClass().getSimpleName()).toList().toString();
    }

    /**
     * 管理员位是刻意的：本类测的是竞态，断言不该对仓库授权守卫敏感。
     * 每次调用都新建线程，登录态是 ThreadLocal，因此必须在子线程里重设。
     */
    private static void setThreadOperator() {
        var employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setActualName("Dispatch concurrency IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(true);
        SmartRequestUtil.setRequestUser(employee);
    }

    private Long locatedWarehouse(String tag) {
        Long warehouseId = newWarehouse(tag);
        jdbc.update("UPDATE warehouse SET longitude = 113.90, latitude = 22.50, geom_crs = 'GCJ02' WHERE id = ?",
                warehouseId);
        createdWarehouses.add(warehouseId);
        evictMybatisCache();
        return warehouseId;
    }

    /**
     * 本类每个用例都是真提交，建的仓库会留在库里。既有 IT（例如出库预留那组）里有按
     * 「唯一启用仓库」解析默认仓的链路（41018），多留一个启用仓就会把它们改成红的 ——
     * 那不是被发现的缺陷，而是夹具污染。所以用完立刻停用。
     *
     * <p>这里刻意走直改状态而不是 {@code WarehouseService#disable}：本类要验的是发车竞态，
     * 不是停用守卫；用服务接口反而会被在途 / 余额守卫挡下来，把那条规则的错误码混进本类的失败里。
     */
    @AfterEach
    void retireWarehousesCreatedHere() {
        createdWarehouses.forEach(this::disableWarehouse);
        createdWarehouses.clear();
    }

    private void stockIn(Long warehouseId, Long skuId, String tag, String quantity) {
        Long supplierId = newPurchasableSupplier(tag, skuId);
        PurchaseOrderVO order = purchaseOrderService.create(
                orderForm(supplierId, warehouseId, skuId, quantity, "6.2000"), prefix + ":" + tag + ":po");
        submitOrder(order.getId());
        confirmReceipt(createReceipt(order.getId()).getId(), quantity);
        evictMybatisCache();
    }

    private Long sortedOrder(String tag, Long skuId, Long warehouseId, String sortedQuantity) {
        Long customerId = newCustomer();
        jdbc.update("UPDATE customer SET address=?,longitude=113.94,latitude=22.54,geom_crs='GCJ02' WHERE id = ?",
                "并发 IT 地址" + tag, customerId);
        Long orderId = confirmedSalesOrder(customerId, skuId, "5.0000", "5.0000");
        Long orderItemId = confirmedSalesOrderItemId(orderId);

        var create = new SortingTaskCreateForm();
        create.setWarehouseId(warehouseId);
        create.setAssigneeEmployeeId(1L);
        create.setRemark("发车并发前置夹具");
        create.setSalesOrderItemIds(new ArrayList<>(List.of(orderItemId)));
        var detail = sorting.create(create, prefix + ":sort:" + orderId);
        Long taskId = detail.getTask().getId();
        var entry = new SortingEntryItemForm();
        entry.setId(detail.getItems().getFirst().getId());
        entry.setVersion(detail.getItems().getFirst().getVersion());
        entry.setSortedQuantity(new BigDecimal(sortedQuantity));
        entry.setResult("NORMAL");
        var entryForm = new SortingEntryForm();
        entryForm.setItems(new ArrayList<>(List.of(entry)));
        sorting.enter(taskId, entryForm);
        sorting.complete(taskId, reopenForm(taskId));
        evictMybatisCache();
        return orderId;
    }

    private Long plannedRoute(Long warehouseId, Long... orderIds) {
        var form = new DeliveryRouteForm();
        form.setRouteName("发车并发线路");
        form.setDeliveryDate(LocalDate.now());
        form.setWarehouseId(warehouseId);
        Long routeId = routeService.create(form);
        for (Long orderId : orderIds) {
            var attach = new DeliveryOrdersForm();
            attach.setVersion(versionOf(routeId));
            attach.setOrderIds(List.of(orderId));
            attach.setReason("组单");
            routeService.addOrders(routeId, attach);
        }
        jdbc.update("UPDATE delivery_route_stop SET longitude = 113.95, latitude = 22.55, geom_crs = 'GCJ02'"
                + " WHERE route_id = ? AND deleted = FALSE", routeId);
        evictMybatisCache();
        routeService.plan(routeId, dispatchForm(routeId));
        return routeId;
    }

    private Long taskOfOrderLine(Long orderId) {
        return jdbc.queryForObject("SELECT si.task_id FROM sorting_task_item si"
                        + " JOIN sales_order_item i ON i.id = si.sales_order_item_id"
                        + " WHERE i.order_id = ? AND si.deleted = FALSE AND si.occupation_status = 'ACTIVE'",
                Long.class, orderId);
    }

    private DeliveryVersionForm dispatchForm(Long routeId) {
        var form = new DeliveryVersionForm();
        form.setVersion(versionOf(routeId));
        return form;
    }

    private SortingActionForm reopenForm(Long taskId) {
        var form = new SortingActionForm();
        form.setVersion(jdbc.queryForObject("SELECT version FROM sorting_task WHERE id = ?", Integer.class, taskId));
        form.setReason("并发重开");
        return form;
    }

    private Integer versionOf(Long routeId) {
        return jdbc.queryForObject("SELECT version FROM delivery_route WHERE id = ?", Integer.class, routeId);
    }

    private String key(String tag) {
        return prefix + ":" + tag + ":" + UUID.randomUUID();
    }

    private String routeStatus(Long routeId) {
        return jdbc.queryForObject("SELECT status FROM delivery_route WHERE id = ?", String.class, routeId);
    }

    private int outboundCountForRoute(Long routeId) {
        return jdbc.queryForObject("SELECT count(*) FROM inventory_outbound WHERE source_document_id = ?"
                + " AND source_document_type = 'DELIVERY_ROUTE' AND deleted = FALSE", Integer.class, routeId);
    }

    private int movementCountForRoute(Long routeId) {
        return jdbc.queryForObject("SELECT count(*) FROM inventory_movement m"
                + " JOIN inventory_outbound o ON o.id = m.source_document_id"
                + " WHERE o.source_document_id = ? AND o.source_document_type = 'DELIVERY_ROUTE'"
                + " AND m.source_document_type = 'SALES_OUTBOUND_ITEM'", Integer.class, routeId);
    }

    private BigDecimal onHand(Long warehouseId, Long skuId) {
        return inventoryBalanceDao.lockByWarehouseAndSku(warehouseId, skuId).getQuantity();
    }

    /** 任何时刻都不允许出现「预留量超过现有量」——库里也有同一条 CHECK，这里只是把竞态后的账读出来。 */
    private boolean reservedNeverGoesAboveOnHand(Long warehouseId, Long skuId) {
        var balance = inventoryBalanceDao.lockByWarehouseAndSku(warehouseId, skuId);
        return balance.getReservedQuantity().compareTo(balance.getQuantity()) <= 0;
    }
}
