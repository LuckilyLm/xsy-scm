package net.lab1024.sa.admin.module.scm.finance;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryOrdersForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryRouteForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliverySignForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryVersionForm;
import net.lab1024.sa.admin.module.scm.delivery.service.DeliveryRouteService;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderReturnAddForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderReturnApproveForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderReturnApproveItemForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderReturnItemForm;
import net.lab1024.sa.admin.module.scm.order.domain.vo.OrderReturnDetailVO;
import net.lab1024.sa.admin.module.scm.order.service.OrderReturnService;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingActionForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingEntryForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingEntryItemForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingTaskCreateForm;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 签收与退货批准**真并发**下的红字收敛（F1-2C，PG IT，无外层事务）。
 *
 * <p><b>要防的是漏账，不是重复账</b>：{@code approve} 与 {@code sign} 是两条独立事务。
 * 若「批准时看一眼有没有正常应收、签收时看一眼有没有已批准退货」两边的读取都不被定序，
 * 就会出现两边各自看不到对方、两边都跳过、提交后永久只剩一张正常应收的局面 ——
 * 来源唯一索引修不了「没有人尝试 INSERT」。
 *
 * <p>这里的定序点是订单行锁：{@code OrderReturnService.lock} 一开始就 {@code orders.lock(orderId)}，
 * F1-2C 让 {@code DeliveryRouteService.sign} 在 {@code markSigned} 之前按
 * {@code route → sales_order} 锁同一行（与本域 dispatch / plan / addOrders 同序）。
 * 后拿到锁的一方在 {@code READ COMMITTED} 下必然看见先提交的一方，
 * 于是两条路径都调用同一个红字算法，其中一次真正生成、另一次命中来源唯一索引静默返回。
 *
 * <p>断言只看**最终事实**，不断言哪个线程一定先成功 —— 先后取决于调度，写死顺序的断言
 * 只是在测调度器。同时把两边的异常都收上来断言为空，因此死锁 / 超时 / 一方被牺牲都会红。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("签收与退货批准并发下的红字收敛（F1-2C，PG IT，无外层事务）")
class ScmFinanceReceivableRedRacePgIT extends ScmW6PgITBase {

    @Autowired
    private DeliveryRouteService routeService;

    @Autowired
    private OrderReturnService returns;

    @Override
    protected void evictMybatisCache() {
        // 无外层事务 → 每次 DAO 调用都是新 session → 一级缓存天然为空
    }

    private record Prepared(Long routeId, Long warehouseId, Long orderId, Long orderItemId,
                            Long returnId, Integer returnVersion, Integer signVersion) {
    }

    /** 真实链路建到「已发车在途 + 退货单待批准」，两条竞争事务的前置全部已提交。 */
    private Prepared prepared(String tag) {
        Long warehouseId = newWarehouse(tag);
        jdbc.update("UPDATE warehouse SET longitude = 113.90, latitude = 22.50, geom_crs = 'GCJ02' WHERE id = ?",
                warehouseId);
        Long skuId = newOnShelfSku(tag);
        Long supplierId = newPurchasableSupplier(tag, skuId);
        PurchaseOrderVO purchase = purchaseOrderService.create(
                orderForm(supplierId, warehouseId, skuId, "1000.0000", "6.2000"), prefix + ":" + tag + ":po");
        submitOrder(purchase.getId());
        confirmReceipt(createReceipt(purchase.getId()).getId(), "1000.0000");

        Long customerId = newCustomer();
        jdbc.update("UPDATE customer SET address = ?, longitude = 113.94, latitude = 22.54,"
                + " geom_crs = 'GCJ02' WHERE id = ?", "F1-2C 并发地址", customerId);
        Long orderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
        Long orderItemId = confirmedSalesOrderItemId(orderId);

        var createTask = new SortingTaskCreateForm();
        createTask.setWarehouseId(warehouseId);
        createTask.setAssigneeEmployeeId(1L);
        createTask.setRemark("F1-2C 并发前置");
        createTask.setSalesOrderItemIds(new ArrayList<>(List.of(orderItemId)));
        var detail = sortingTaskService.create(createTask, key("sort"));
        var entry = new SortingEntryItemForm();
        entry.setId(detail.getItems().getFirst().getId());
        entry.setVersion(detail.getItems().getFirst().getVersion());
        entry.setSortedQuantity(new BigDecimal("10.0000"));
        entry.setResult("NORMAL");
        var entryForm = new SortingEntryForm();
        entryForm.setItems(new ArrayList<>(List.of(entry)));
        Long taskId = detail.getTask().getId();
        sortingTaskService.enter(taskId, entryForm);
        var action = new SortingActionForm();
        action.setVersion(jdbc.queryForObject("SELECT version FROM sorting_task WHERE id = ?",
                Integer.class, taskId));
        sortingTaskService.complete(taskId, action);

        var routeForm = new DeliveryRouteForm();
        routeForm.setRouteName("F1-2C 并发线路");
        routeForm.setDeliveryDate(LocalDate.now());
        routeForm.setWarehouseId(warehouseId);
        Long routeId = routeService.create(routeForm);
        var attach = new DeliveryOrdersForm();
        attach.setVersion(routeVersion(routeId));
        attach.setOrderIds(List.of(orderId));
        attach.setReason("组单");
        routeService.addOrders(routeId, attach);
        jdbc.update("UPDATE delivery_route_stop SET longitude = 113.95, latitude = 22.55, geom_crs = 'GCJ02'"
                + " WHERE route_id = ? AND deleted = FALSE", routeId);
        routeService.plan(routeId, routeForm2(routeId));
        routeService.dispatch(routeId, routeForm2(routeId), key("dispatch"));

        var returnAdd = new OrderReturnAddForm();
        returnAdd.setOrderId(orderId);
        returnAdd.setReason("品质问题 并发");
        var returnLine = new OrderReturnItemForm();
        returnLine.setOrderItemId(orderItemId);
        returnLine.setRequestedQuantity("4.0000");
        returnAdd.setItems(List.of(returnLine));
        OrderReturnDetailVO created = returns.create(returnAdd, key("return-create"));

        Integer signVersion = jdbc.queryForObject(
                "SELECT version FROM delivery_route_order WHERE route_id = ? AND order_id = ? AND deleted = FALSE",
                Integer.class, routeId, orderId);
        return new Prepared(routeId, warehouseId, orderId, orderItemId, created.getReturnId(),
                created.getVersion(), signVersion);
    }

    private Integer routeVersion(Long routeId) {
        return jdbc.queryForObject("SELECT version FROM delivery_route WHERE id = ?", Integer.class, routeId);
    }

    private DeliveryVersionForm routeForm2(Long routeId) {
        var form = new DeliveryVersionForm();
        form.setVersion(routeVersion(routeId));
        return form;
    }

    private String key(String tag) {
        return prefix + ":" + tag + ":" + UUID.randomUUID();
    }

    /**
     * 工作线程必须自带请求身份：{@code SmartRequestUtil} 是 ThreadLocal，
     * 而签收人与批准人要落进财务事实（{@code signed_by} / {@code updated_by}）。
     */
    private void login(String name) {
        var employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setActualName(name);
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(true);
        SmartRequestUtil.setRequestUser(employee);
    }

    private void run(String name, Runnable body, AtomicReference<Throwable> failure,
                     CountDownLatch ready, CountDownLatch start, CountDownLatch done) {
        new Thread(() -> {
            login(name);
            try {
                ready.countDown();
                start.await();
                body.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                SmartRequestUtil.remove();
                done.countDown();
            }
        }, name).start();
    }

    @Test
    @DisplayName("两条事务同时冲同一订单：最终 1 张正常 + 1 张红字，无漏账无重复无死锁")
    void concurrentSignAndApprovalConvergeToCompleteFacts() throws Exception {
        Prepared prepared = prepared("RACE1");
        Long orderId = prepared.orderId();

        var signFailure = new AtomicReference<Throwable>();
        var approveFailure = new AtomicReference<Throwable>();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(2);

        var signForm = new DeliverySignForm();
        signForm.setVersion(prepared.signVersion());
        signForm.setResult("SIGNED");

        var approve = new OrderReturnApproveForm();
        approve.setReturnId(prepared.returnId());
        approve.setVersion(prepared.returnVersion());
        var approvedLine = new OrderReturnApproveItemForm();
        approvedLine.setOrderItemId(prepared.orderItemId());
        approvedLine.setApprovedQuantity("4.0000");
        approve.setItems(List.of(approvedLine));

        try {
            run("sign-thread",
                    () -> routeService.sign(prepared.routeId(), orderId, signForm),
                    signFailure, ready, start, done);
            run("approve-thread",
                    () -> returns.approve(approve, key("return-approve")),
                    approveFailure, ready, start, done);

            assertThat(ready.await(30, TimeUnit.SECONDS)).as("两个线程都要到达起跑线").isTrue();
            start.countDown();
            assertThat(done.await(60, TimeUnit.SECONDS)).as("两个事务必须在 60 秒内结束（否则疑似死锁等待）")
                    .isTrue();

            assertThat(signFailure.get()).as("签收侧不得失败（死锁会被 PostgreSQL 牺牲成异常）").isNull();
            assertThat(approveFailure.get()).as("批准侧不得失败").isNull();

            // 最终事实：一正一红，红字挂对原单、金额与来源正确
            Map<String, Object> normal = jdbc.queryForMap(
                    "SELECT * FROM finance_receivable WHERE source_type = 'SALES_ORDER'"
                            + " AND entry_type = 'NORMAL' AND source_id = ?", orderId);
            Map<String, Object> red = jdbc.queryForMap(
                    "SELECT * FROM finance_receivable WHERE source_type = 'ORDER_RETURN'"
                            + " AND entry_type = 'RED' AND source_id = ?", prepared.returnId());
            assertThat(((Number) red.get("original_receivable_id")).longValue())
                    .isEqualTo(((Number) normal.get("id")).longValue());
            assertThat((BigDecimal) red.get("amount")).as("4 × 1.2000").isEqualByComparingTo("4.8000");

            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM finance_receivable WHERE order_id = ?", Integer.class, orderId))
                    .as("恰好一张正常 + 一张红字").isEqualTo(2);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM finance_receivable_item WHERE receivable_id = ?",
                    Integer.class, ((Number) red.get("id")).longValue())).isEqualTo(1);

            // 两边都尝试过派生，但每条事实只落一次
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM finance_operation_log WHERE business_type = 'RECEIVABLE'"
                            + " AND business_id = ? AND operation_type = 'GENERATE'",
                    Integer.class, ((Number) normal.get("id")).longValue())).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM finance_operation_log WHERE business_type = 'RECEIVABLE'"
                            + " AND business_id = ? AND operation_type = 'RED_GENERATE'",
                    Integer.class, ((Number) red.get("id")).longValue())).isEqualTo(1);

            // 业务侧终态：签收、批准、退款单三件都在
            assertThat(jdbc.queryForObject(
                    "SELECT fulfillment_status FROM delivery_route_order WHERE route_id = ? AND order_id = ?"
                            + " AND deleted = FALSE", String.class, prepared.routeId(), orderId))
                    .isEqualTo("SIGNED");
            assertThat(jdbc.queryForObject("SELECT status FROM order_return WHERE id = ?", String.class,
                    prepared.returnId())).isEqualTo("APPROVED");
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM order_refund WHERE return_id = ? AND status = 'PENDING'",
                    Integer.class, prepared.returnId())).isEqualTo(1);
        } finally {
            disableWarehouse(prepared.warehouseId());
        }
    }
}
