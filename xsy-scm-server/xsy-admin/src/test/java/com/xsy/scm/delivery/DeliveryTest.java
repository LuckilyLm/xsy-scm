package com.xsy.scm.delivery;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.xsy.scm.admin.module.system.login.domain.RequestEmployee;
import com.xsy.scm.base.common.code.UserErrorCode;
import com.xsy.scm.base.common.enumeration.UserTypeEnum;
import com.xsy.scm.base.common.util.XsyRequestUtil;
import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.ScmW6PgITBase;
import com.xsy.scm.common.handler.ScmExceptionHandler;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.delivery.controller.DeliveryRouteController;
import com.xsy.scm.delivery.domain.entity.DeliveryPlanProposalEntity;
import com.xsy.scm.delivery.domain.entity.DeliveryRouteEntity;
import com.xsy.scm.delivery.domain.entity.DeliveryRouteStopEntity;
import com.xsy.scm.delivery.domain.form.*;
import com.xsy.scm.delivery.domain.form.DeliveryDriverForm;
import com.xsy.scm.delivery.domain.form.DeliveryOrdersForm;
import com.xsy.scm.delivery.domain.form.DeliveryPrintOrdersForm;
import com.xsy.scm.delivery.domain.form.DeliveryQueryForm;
import com.xsy.scm.delivery.domain.form.DeliveryRouteForm;
import com.xsy.scm.delivery.domain.form.DeliverySignForm;
import com.xsy.scm.delivery.domain.form.DeliveryVersionForm;
import com.xsy.scm.delivery.domain.vo.*;
import com.xsy.scm.delivery.domain.vo.DeliveryOrderViewVO;
import com.xsy.scm.delivery.domain.vo.DeliveryPlanProposalVO;
import com.xsy.scm.delivery.domain.vo.DeliveryRouteVO;
import com.xsy.scm.delivery.service.*;
import com.xsy.scm.delivery.service.DeliveryCandidateOrderQueryService;
import com.xsy.scm.delivery.service.DeliveryDriverService;
import com.xsy.scm.delivery.service.DeliveryRouteQueryService;
import com.xsy.scm.delivery.service.DeliveryRouteService;
import com.xsy.scm.delivery.support.DeliveryPlanSnapshotPolicy;
import com.xsy.scm.order.domain.form.OrderCancelForm;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderVO;
import com.xsy.scm.sorting.domain.entity.SortingTaskEntity;
import com.xsy.scm.sorting.domain.form.SortingActionForm;
import com.xsy.scm.sorting.domain.form.SortingEntryForm;
import com.xsy.scm.sorting.domain.form.SortingEntryItemForm;
import com.xsy.scm.sorting.domain.form.SortingTaskCreateForm;
import com.xsy.scm.sorting.service.SortingTaskService;
import com.xsy.scm.warehouse.domain.form.WarehouseAddForm;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static com.xsy.scm.delivery.constant.DeliveryErrorCode.DISPATCH_ROUTE_INELIGIBLE;
import static com.xsy.scm.delivery.constant.DeliveryErrorCode.ROUTE_NOT_ALL_SIGNED;
import static com.xsy.scm.delivery.constant.DeliveryErrorCode.SIGN_REASON_REQUIRED;
import static com.xsy.scm.delivery.constant.DeliveryErrorCode.STATE_INVALID;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_IDEMPOTENCY_CONFLICT;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_IDEMPOTENCY_KEY_REQUIRED;
import static com.xsy.scm.sorting.constant.SortingErrorCode.OUTBOUND_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

class DeliveryTest {

    @Nested
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
                    "SELECT count(*) FROM xsy_delivery_route_order WHERE route_id = ? AND fulfillment_status = 'IN_TRANSIT'"
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
            String taskStatus = jdbc.queryForObject("SELECT status FROM xsy_sorting_task WHERE id = ?", String.class, taskId);

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
                            XsyRequestUtil.remove();
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
         * 这里用管理员位：本类测的是竞态，断言不该对仓库授权守卫敏感。
         * 每次调用都新建线程，登录态是 ThreadLocal，因此必须在子线程里重设。
         */
        private static void setThreadOperator() {
            var employee = new RequestEmployee();
            employee.setEmployeeId(1L);
            employee.setActualName("Dispatch concurrency IT");
            employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            employee.setAdministratorFlag(true);
            XsyRequestUtil.setRequestUser(employee);
        }

        private Long locatedWarehouse(String tag) {
            Long warehouseId = newWarehouse(tag);
            jdbc.update("UPDATE xsy_warehouse SET longitude = 113.90, latitude = 22.50, geom_crs = 'GCJ02' WHERE id = ?",
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
         * <p>这里直接改状态而不走 {@code WarehouseService#disable}：本类要验的是发车竞态，
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
            jdbc.update("UPDATE xsy_customer SET address=?,longitude=113.94,latitude=22.54,geom_crs='GCJ02' WHERE id = ?",
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
            jdbc.update("UPDATE xsy_delivery_route_stop SET longitude = 113.95, latitude = 22.55, geom_crs = 'GCJ02'"
                    + " WHERE route_id = ? AND deleted = FALSE", routeId);
            evictMybatisCache();
            routeService.plan(routeId, dispatchForm(routeId));
            return routeId;
        }

        private Long taskOfOrderLine(Long orderId) {
            return jdbc.queryForObject("SELECT si.task_id FROM xsy_sorting_task_item si"
                            + " JOIN xsy_sales_order_item i ON i.id = si.sales_order_item_id"
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
            form.setVersion(jdbc.queryForObject("SELECT version FROM xsy_sorting_task WHERE id = ?", Integer.class, taskId));
            form.setReason("并发重开");
            return form;
        }

        private Integer versionOf(Long routeId) {
            return jdbc.queryForObject("SELECT version FROM xsy_delivery_route WHERE id = ?", Integer.class, routeId);
        }

        private String key(String tag) {
            return prefix + ":" + tag + ":" + UUID.randomUUID();
        }

        private String routeStatus(Long routeId) {
            return jdbc.queryForObject("SELECT status FROM xsy_delivery_route WHERE id = ?", String.class, routeId);
        }

        private int outboundCountForRoute(Long routeId) {
            return jdbc.queryForObject("SELECT count(*) FROM xsy_inventory_outbound WHERE source_document_id = ?"
                    + " AND source_document_type = 'DELIVERY_ROUTE' AND deleted = FALSE", Integer.class, routeId);
        }

        private int movementCountForRoute(Long routeId) {
            return jdbc.queryForObject("SELECT count(*) FROM xsy_inventory_movement m"
                    + " JOIN xsy_inventory_outbound o ON o.id = m.source_document_id"
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

    @Nested
    @DisplayName("配送 L3 发车 / 签收 / 完成线路（PG IT）")
    class DeliveryDispatchPgIT extends ScmW6PgITBase {
        private static final String ADDRESSED = "L3 IT 地址";

        @Autowired
        private DeliveryRouteService routeService;

        @Autowired
        private DeliveryDriverService driverService;

        @Test
        @DisplayName("发车：线路进 DISPATCHED、写出 outbound_id、订单进在途、库存按实发量扣")
        void dispatchProducesOutboundAndMovesOrdersInTransit() {
            Case one = plannedRouteWithOneSortedOrder("l3a", "3.0000");

            var result = routeService.dispatch(one.routeId(), dispatchForm(one.routeId()), key("dispatch"));

            assertThat(result.getOutboundId()).as("有实物离仓就要有出库单").isNotNull();
            assertThat(result.getOrderCount()).isEqualTo(1);
            assertThat(result.getShippedLineCount()).isEqualTo(1);
            assertThat(routeStatus(one.routeId())).isEqualTo("DISPATCHED");
            assertThat(jdbc.queryForObject("SELECT outbound_id FROM xsy_delivery_route WHERE id = ?", Long.class, one.routeId()))
                    .as("裁决第 3 条那条追溯链的起点必须真的写入数据库").isEqualTo(result.getOutboundId());
            assertThat(jdbc.queryForObject("SELECT dispatched_by FROM xsy_delivery_route WHERE id = ?", String.class, one.routeId()))
                    .as("发车时点必须成对留下操作人").isNotBlank();
            assertThat(fulfillmentStatus(one.routeId(), one.orderId())).isEqualTo("IN_TRANSIT");
            assertThat(salesOrderService.lock(one.orderId()).getStatus()).isEqualTo("DISPATCHED");
            assertThat(onHand(one.warehouseId(), one.skuId())).as("扣的是分拣实发量 3，不是订单行数量").isEqualByComparingTo("7.0000");
            assertThat(jdbc.queryForObject(
                    "SELECT i.sales_order_item_id FROM xsy_inventory_outbound_item i WHERE i.outbound_id = ?",
                    Long.class, result.getOutboundId())).isEqualTo(one.orderItemId());
        }

        @Test
        @DisplayName("同一 Idempotency-Key 重发时返回原结果，不重复扣库存")
        void repeatedDispatchWithSameKeyReplaysOriginalResult() {
            Case one = plannedRouteWithOneSortedOrder("l3b", "4.0000");
            String key = key("dispatch-once");
            // 重放要求载荷逐字相同（version 也在哈希里），所以这里复用同一个 form ——
            // 真实客户端的重试是 axios 原样重发，不是重新读一遍 version 再组一次请求。
            var form = dispatchForm(one.routeId());

            var first = routeService.dispatch(one.routeId(), form, key);
            BigDecimal afterFirst = onHand(one.warehouseId(), one.skuId());
            var replay = routeService.dispatch(one.routeId(), form, key);

            assertThat(replay.getOutboundId()).isEqualTo(first.getOutboundId());
            assertThat(onHand(one.warehouseId(), one.skuId())).as("重放不能再扣一次").isEqualByComparingTo(afterFirst);
            assertThat(outboundCountForRoute(one.routeId())).as("一条线路一张出库单").isEqualTo(1);

            // 同一把键配上不同载荷必须被拒，而不是把新内容当成上一次的静默重放。
            expectCode(() -> routeService.dispatch(one.routeId(), dispatchForm(one.routeId()), key),
                    ORDER_IDEMPOTENCY_CONFLICT.getCode());
        }

        @Test
        @DisplayName("缺 Idempotency-Key 的发车直接拒绝，线路仍在 PLANNED")
        void dispatchWithoutIdempotencyKeyIsRejected() {
            Case one = plannedRouteWithOneSortedOrder("l3c", "2.0000");

            expectCode(() -> routeService.dispatch(one.routeId(), dispatchForm(one.routeId()), null),
                    ORDER_IDEMPOTENCY_KEY_REQUIRED.getCode());

            assertThat(routeStatus(one.routeId())).isEqualTo("PLANNED");
            assertThat(onHand(one.warehouseId(), one.skuId())).isEqualByComparingTo("10.0000");
        }

        @Test
        @DisplayName("线路上任一订单被重开：整条线路拒绝发车，库存一行都不动")
        void anyIneligibleOrderBlocksTheWholeRoute() {
            Case one = plannedRouteWithTwoSortedOrders("l3d", "3.0000");
            reopenTaskOf(one.secondOrderItemId());

            expectCode(() -> routeService.dispatch(one.routeId(), dispatchForm(one.routeId()), key("dispatch")),
                    DISPATCH_ROUTE_INELIGIBLE.getCode());

            assertThat(routeStatus(one.routeId())).as("整条线路留在 PLANNED，不出现半发车").isEqualTo("PLANNED");
            assertThat(onHand(one.warehouseId(), one.skuId())).isEqualByComparingTo("10.0000");
            assertThat(outboundCountForRoute(one.routeId())).isZero();
            assertThat(salesOrderService.lock(one.orderId()).getStatus()).isEqualTo("CONFIRMED");
        }

        @Test
        @DisplayName("发车之后：分拣任务不能重开（P1 裁决第 21 条的守卫）")
        void reopenIsBlockedAfterRealOutbound() {
            Case one = plannedRouteWithOneSortedOrder("l3e", "3.0000");
            routeService.dispatch(one.routeId(), dispatchForm(one.routeId()), key("dispatch"));
            Long taskId = taskOfOrderItem(one.orderItemId());

            expectCode(() -> sortingTaskService.reopen(taskId, actionForm(taskId)), OUTBOUND_EXISTS.getCode());

            assertThat(jdbc.queryForObject("SELECT status FROM xsy_sorting_task WHERE id = ?", String.class, taskId))
                    .as("被拒的重开没有改任务状态").isEqualTo("COMPLETED");
        }

        @Test
        @DisplayName("签收与完成：异常必须有原因，全部活动订单终态之后线路才能完成")
        void signAndCompleteFollowOrderLevelFulfillment() {
            Case one = plannedRouteWithTwoSortedOrders("l3f", "2.0000");
            routeService.dispatch(one.routeId(), dispatchForm(one.routeId()), key("dispatch"));

            DeliverySignForm blank = signForm(one.routeId(), one.orderId(), "EXCEPTION", null);
            expectCode(() -> routeService.sign(one.routeId(), one.orderId(), blank), SIGN_REASON_REQUIRED.getCode());

            routeService.sign(one.routeId(), one.orderId(), signForm(one.routeId(), one.orderId(), "EXCEPTION", "客户拒收"));
            assertThat(fulfillmentStatus(one.routeId(), one.orderId())).isEqualTo("EXCEPTION");
            assertThat(salesOrderService.lock(one.orderId()).getStatus()).isEqualTo("EXCEPTION");
            expectCode(() -> routeService.complete(one.routeId(), versionForm(one.routeId())), ROUTE_NOT_ALL_SIGNED.getCode());

            routeService.sign(one.routeId(), one.secondOrderId(), signForm(one.routeId(), one.secondOrderId(), "SIGNED", null));
            assertThat(salesOrderService.lock(one.secondOrderId()).getStatus()).isEqualTo("SIGNED");
            routeService.complete(one.routeId(), versionForm(one.routeId()));

            assertThat(routeStatus(one.routeId())).isEqualTo("COMPLETED");
            assertThat(jdbc.queryForObject("SELECT completed_by FROM xsy_delivery_route WHERE id = ?", String.class, one.routeId()))
                    .isNotBlank();
            assertThat(fulfillmentStatus(one.routeId(), one.orderId())).as("异常签收也是终态").isEqualTo("EXCEPTION");
        }

        @Test
        @DisplayName("状态守卫：PENDING 不能发车，DISPATCHED 之后不能取消，同一订单不能被签两次")
        void stateGuardsKeepTransitionsIrreversible() {
            Case one = plannedRouteWithOneSortedOrder("l3g", "1.0000");
            Long draftRoute = newRoute(seedWarehouseId());
            expectCode(() -> routeService.dispatch(draftRoute, dispatchForm(draftRoute), key("draft")), STATE_INVALID.getCode());

            routeService.dispatch(one.routeId(), dispatchForm(one.routeId()), key("dispatch"));
            expectCode(() -> routeService.cancel(one.routeId(), cancelForm(one.routeId())), STATE_INVALID.getCode());

            routeService.sign(one.routeId(), one.orderId(), signForm(one.routeId(), one.orderId(), "SIGNED", null));
            expectCode(() -> routeService.sign(one.routeId(), one.orderId(), signForm(one.routeId(), one.orderId(), "SIGNED", null)),
                    VERSION_CONFLICT.getCode());
        }

        @Test
        @DisplayName("非超管取证：没有仓库授权的调度不能发车，授权后同一调用正常落账")
        void dispatchNeedsWarehouseAuthorizationOfTheCaller() {
            Case one = plannedRouteWithOneSortedOrder("l3h", "2.0000");
            Long dispatcher = newEmployee("l3h");

            expectCodeRuntime(() -> as(dispatcher, () -> routeService.dispatch(one.routeId(), dispatchForm(one.routeId()),
                    key("scope"))), ScmDataScopeException.class);
            assertThat(routeStatus(one.routeId())).as("被拒不留下任何痕迹").isEqualTo("PLANNED");
            assertThat(onHand(one.warehouseId(), one.skuId())).isEqualByComparingTo("10.0000");

            authorize(dispatcher, one.warehouseId());
            var result = as(dispatcher, () -> routeService.dispatch(one.routeId(), dispatchForm(one.routeId()),
                    key("scope2")));

            assertThat(result.getOutboundId()).isNotNull();
            assertThat(routeStatus(one.routeId())).isEqualTo("DISPATCHED");
            assertThat(onHand(one.warehouseId(), one.skuId())).isEqualByComparingTo("8.0000");
        }

        @Test
        @DisplayName("非超管取证：司机只能签绑定到本人的线路，别人线路的签收按无权处理")
        void driverCanOnlySignRoutesOfItsOwnBoundDriverProfile() {
            Case mine = plannedRouteWithOneSortedOrder("l3i", "1.0000");
            Case theirs = plannedRouteWithOneSortedOrder("l3j", "1.0000");
            Long driverEmployee = newEmployee("l3i");
            rebindDriver(mine.routeId(), driverEmployee);
            routeService.dispatch(mine.routeId(), dispatchForm(mine.routeId()), key("mine"));
            routeService.dispatch(theirs.routeId(), dispatchForm(theirs.routeId()), key("theirs"));

            expectCodeRuntime(() -> as(driverEmployee, () -> {
                routeService.sign(theirs.routeId(), theirs.orderId(),
                        signForm(theirs.routeId(), theirs.orderId(), "SIGNED", null));
                return null;
            }), ScmDataScopeException.class);
            as(driverEmployee, () -> {
                routeService.sign(mine.routeId(), mine.orderId(), signForm(mine.routeId(), mine.orderId(), "SIGNED", null));
                return null;
            });

            assertThat(fulfillmentStatus(mine.routeId(), mine.orderId())).isEqualTo("SIGNED");
            assertThat(fulfillmentStatus(theirs.routeId(), theirs.orderId())).isEqualTo("IN_TRANSIT");
        }

        // ------------------------------------------------------------------
        // 夹具
        // ------------------------------------------------------------------

        /** 一条已规划线路所需的全部 id：仓库固定用种子仓，出库扣哪个仓因此是可断言的。 */
        private record Case(Long routeId, Long warehouseId, Long skuId, Long orderId, Long orderItemId,
                            Long secondOrderId, Long secondOrderItemId) {
        }

        private Case plannedRouteWithOneSortedOrder(String tag, String sortedQuantity) {
            return plannedRoute(tag, 1, sortedQuantity);
        }

        private Case plannedRouteWithTwoSortedOrders(String tag, String sortedQuantity) {
            return plannedRoute(tag, 2, sortedQuantity);
        }

        private Case plannedRoute(String tag, int orderCount, String sortedQuantity) {
            // 规划要求「仓库起点 + 每个停靠点都有坐标且同一坐标系」，V15 种子仓没有经纬度，
            // 所以线路仓必须是本用例自己带坐标的仓；库存也入到这个仓，发车扣哪一仓才是可断言的。
            Long warehouseId = locatedWarehouse(tag);
            Long skuId = newOnShelfSku(tag);
            stockIn(warehouseId, skuId, tag, "10.0000");
            Long firstOrder = sortedLocatedOrder(tag + "1", skuId, sortedQuantity, warehouseId);
            Long routeId = newRoute(warehouseId);
            attach(routeId, firstOrder);
            Long secondOrder = null;
            if (orderCount > 1) {
                secondOrder = sortedLocatedOrder(tag + "2", skuId, sortedQuantity, warehouseId);
                attach(routeId, secondOrder);
            }
            locateAllStops(routeId);
            plan(routeId);
            return new Case(routeId, warehouseId, skuId, firstOrder, orderItemOf(firstOrder), secondOrder,
                    secondOrder == null ? null : orderItemOf(secondOrder));
        }

        /** 地址齐备 + 已确认 + 分拣已完成（配送候选的硬前置）。 */
        private Long locatedWarehouse(String tag) {
            // 三列成对写入，满足 V40 的 geom_crs 配对 CHECK；创建走真实服务，只补坐标。
            Long warehouseId = newWarehouse(tag);
            jdbc.update("UPDATE xsy_warehouse SET longitude = 113.90, latitude = 22.50, geom_crs = 'GCJ02' WHERE id = ?",
                    warehouseId);
            evictMybatisCache();
            return warehouseId;
        }

        private Long sortedLocatedOrder(String tag, Long skuId, String sortedQuantity, Long warehouseId) {
            Long customerId = newCustomer();
            jdbc.update("UPDATE xsy_customer SET address=?,longitude=113.94,latitude=22.54,geom_crs='GCJ02' WHERE id = ?",
                    ADDRESSED + tag, customerId);
            Long orderId = confirmedSalesOrder(customerId, skuId, "5.0000", "5.0000");
            sortOneLine(orderId, sortedQuantity, warehouseId);
            return orderId;
        }

        private void sortOneLine(Long orderId, String sortedQuantity, Long warehouseId) {
            Long orderItemId = orderItemOf(orderId);
            var create = new SortingTaskCreateForm();
            create.setWarehouseId(warehouseId);
            create.setAssigneeEmployeeId(currentEmployeeId());
            create.setRemark("发车前置夹具");
            create.setSalesOrderItemIds(new ArrayList<>(List.of(orderItemId)));
            var detail = sortingTaskService.create(create, prefix + ":sort:" + orderId);
            Long taskId = detail.getTask().getId();

            var entry = new SortingEntryItemForm();
            // 录入的是分拣明细行的 id，不是销售订单行 id —— 两者不是一张表的主键。
            entry.setId(detail.getItems().getFirst().getId());
            entry.setVersion(detail.getItems().getFirst().getVersion());
            entry.setSortedQuantity(new BigDecimal(sortedQuantity));
            boolean shortPick = new BigDecimal(sortedQuantity).compareTo(BigDecimal.ZERO) == 0;
            entry.setResult(shortPick ? "OUT_OF_STOCK" : "NORMAL");
            entry.setReason(shortPick ? "现场无货" : null);
            var entries = new ArrayList<SortingEntryItemForm>();
            entries.add(entry);
            var entryForm = new SortingEntryForm();
            entryForm.setItems(entries);
            sortingTaskService.enter(taskId, entryForm);
            sortingTaskService.complete(taskId, actionForm(taskId));
        }

        private void reopenTaskOf(Long orderItemId) {
            Long taskId = taskOfOrderItem(orderItemId);
            sortingTaskService.reopen(taskId, actionForm(taskId));
        }

        private Long taskOfOrderItem(Long orderItemId) {
            return jdbc.queryForObject("SELECT si.task_id FROM xsy_sorting_task_item si"
                            + " WHERE si.sales_order_item_id = ? AND si.deleted = FALSE AND si.occupation_status = 'ACTIVE'",
                    Long.class, orderItemId);
        }

        private Long newRoute(Long warehouseId) {
            var form = new DeliveryRouteForm();
            form.setRouteName("发车 IT 线路");
            form.setDeliveryDate(LocalDate.now());
            form.setWarehouseId(warehouseId);
            return routeService.create(form);
        }

        /** 停靠点坐标来自下单时保存的下单时保存的地址，所以地址必须在下单前写好。 */
        private void attach(Long routeId, Long orderId) {
            var form = new DeliveryOrdersForm();
            form.setVersion(versionOf(routeId));
            form.setOrderIds(List.of(orderId));
            form.setReason("组单");
            routeService.addOrders(routeId, form);
        }

        /**
         * 补齐停靠点坐标，只为满足 L0–L2 的规划前置（点位齐全且同坐标系）。
         *
         * <p>这条前置本身由 {@code DeliveryRouteServiceIT#requireCompleteSameCrsLocationsAndRemoveEmptyStop}
         * 专门验；本类关心的是发车之后发生什么，所以不重复走「客户地址 → 当时的订单资料 → 停靠点」这条
         * 坐标搬运链，直接给停靠点落坐标。
         */
        private void locateAllStops(Long routeId) {
            jdbc.update("UPDATE xsy_delivery_route_stop SET longitude = 113.95, latitude = 22.55, geom_crs = 'GCJ02'"
                    + " WHERE route_id = ? AND deleted = FALSE", routeId);
            evictMybatisCache();
        }

        private void plan(Long routeId) {
            routeService.plan(routeId, versionForm(routeId));
        }

        /** 把线路改绑到「绑定给定员工的司机档案」上，用来构造司机维度的正反例。 */
        private void rebindDriver(Long routeId, Long employeeId) {
            var driver = new DeliveryDriverForm();
            driver.setDriverCode(prefix + "-DRV" + employeeId);
            driver.setDriverName("发车 IT 司机");
            driver.setPhone("13800000000");
            driver.setEmployeeId(employeeId);
            driver.setStatus("ENABLED");
            Long driverId = driverService.save(driver);
            jdbc.update("UPDATE xsy_delivery_route SET driver_id = ? WHERE id = ?", driverId, routeId);
            evictMybatisCache();
        }

        private String key(String tag) {
            return prefix + ":" + tag + ":" + UUID.randomUUID();
        }

        private DeliveryVersionForm dispatchForm(Long routeId) {
            return versionForm(routeId);
        }

        private DeliveryVersionForm versionForm(Long routeId) {
            var form = new DeliveryVersionForm();
            form.setVersion(versionOf(routeId));
            return form;
        }

        private DeliveryVersionForm cancelForm(Long routeId) {
            var form = versionForm(routeId);
            form.setReason("发车后取消测试");
            return form;
        }

        private DeliverySignForm signForm(Long routeId, Long orderId, String result, String reason) {
            var form = new DeliverySignForm();
            form.setVersion(orderVersionOf(routeId, orderId));
            form.setResult(result);
            form.setReason(reason);
            return form;
        }

        private SortingActionForm actionForm(Long taskId) {
            var form = new SortingActionForm();
            form.setVersion(jdbc.queryForObject("SELECT version FROM xsy_sorting_task WHERE id = ?", Integer.class, taskId));
            form.setReason("发车 IT 重开");
            return form;
        }

        private Integer versionOf(Long routeId) {
            return jdbc.queryForObject("SELECT version FROM xsy_delivery_route WHERE id = ?", Integer.class, routeId);
        }

        private Integer orderVersionOf(Long routeId, Long orderId) {
            return jdbc.queryForObject("SELECT version FROM xsy_delivery_route_order WHERE route_id = ? AND order_id = ?",
                    Integer.class, routeId, orderId);
        }

        private Long orderItemOf(Long orderId) {
            return confirmedSalesOrderItemId(orderId);
        }

        private String routeStatus(Long routeId) {
            return jdbc.queryForObject("SELECT status FROM xsy_delivery_route WHERE id = ?", String.class, routeId);
        }

        private String fulfillmentStatus(Long routeId, Long orderId) {
            return jdbc.queryForObject(
                    "SELECT fulfillment_status FROM xsy_delivery_route_order WHERE route_id = ? AND order_id = ?",
                    String.class, routeId, orderId);
        }

        private int outboundCountForRoute(Long routeId) {
            return jdbc.queryForObject("SELECT count(*) FROM xsy_inventory_outbound WHERE source_document_id = ?"
                    + " AND source_document_type = 'DELIVERY_ROUTE' AND deleted = FALSE", Integer.class, routeId);
        }

        private BigDecimal onHand(Long warehouseId, Long skuId) {
            return inventoryBalanceDao.lockByWarehouseAndSku(warehouseId, skuId).getQuantity();
        }

        /** 真实采购链路入库（入库的唯一合法来源）。 */
        private void stockIn(Long warehouseId, Long skuId, String tag, String quantity) {
            Long supplierId = newPurchasableSupplier(tag, skuId);
            PurchaseOrderVO order = purchaseOrderService.create(
                    orderForm(supplierId, warehouseId, skuId, quantity, "6.2000"), prefix + ":" + tag + ":po");
            submitOrder(order.getId());
            confirmReceipt(createReceipt(order.getId()).getId(), quantity);
            evictMybatisCache();
        }

        // ------------------------------------------------------------------
        // 身份与权限：被测账号一律 administrator_flag = false（P0 裁决第 5 条）
        // ------------------------------------------------------------------

        private Long newEmployee(String tag) {
            loginAsAdmin();
            String loginName = (prefix + "-" + tag).toUpperCase(Locale.ROOT);
            jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                            + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                    UUID.randomUUID().toString().replace("-", ""), loginName, "$argon2id$it-placeholder", "调度" + tag);
            Long employeeId = jdbc.queryForObject(
                    "SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, loginName);
            assertThat(jdbc.queryForObject("SELECT administrator_flag FROM t_employee WHERE employee_id = ?",
                    Boolean.class, employeeId)).as("权限取证禁止超管位").isFalse();
            evictMybatisCache();
            return employeeId;
        }

        private void authorize(Long employeeId, Long warehouseId) {
            jdbc.update("INSERT INTO xsy_employee_warehouse_scope (employee_id, warehouse_id) VALUES (?, ?)",
                    employeeId, warehouseId);
            evictMybatisCache();
        }

        private <T> T as(Long employeeId, Supplier<T> body) {
            var employee = new RequestEmployee();
            employee.setEmployeeId(employeeId);
            employee.setActualName("发车 IT 非超管");
            employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            employee.setAdministratorFlag(false);
            employee.setDepartmentId(1L);
            XsyRequestUtil.setRequestUser(employee);
            try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                // 功能点由 Controller 的 @SaCheckPermission 负责，服务层这一层只依赖范围解析；
                // 全部 hasPermission 保持默认 false，仓库维度就只能来自 employee_warehouse_scope 授权行。
                return body.get();
            } finally {
                loginAsAdmin();
            }
        }

        private void loginAsAdmin() {
            var employee = new RequestEmployee();
            employee.setEmployeeId(1L);
            employee.setActualName("L3 IT");
            employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            employee.setAdministratorFlag(true);
            XsyRequestUtil.setRequestUser(employee);
        }

        private Long currentEmployeeId() {
            return ((RequestEmployee) XsyRequestUtil.getRequestUser()).getEmployeeId();
        }

        /**
         * 断言抛出<b>指定类型</b>的异常：{@code ScmDataScopeException} 不是
         * {@code ScmBusinessException}，用 {@link #expectCode} 那条路子接不住。
         */
        private static void expectCodeRuntime(Runnable action, Class<? extends Throwable> expected) {
            org.assertj.core.api.Assertions.assertThatThrownBy(action::run)
                    .as("越权必须是 %s，不能退化成可读的 404", expected.getSimpleName())
                    .isInstanceOf(expected);
        }
    }

    @Nested
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
         * 但 {@code xsy_warehouse.status = ENABLED} 是全局判据 —— 库存 IT 按「唯一启用仓库」推导默认仓库，
         * 残留一个启用仓库就会让它们整批报「当前启用仓库不是唯一一个」。停用而非删行，
         * 是为了不给 {@code xsy_delivery_route.warehouse_id} 留下悬空引用。
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
        @DisplayName("不同防重复请求编号并发重打同一订单：两次都成功且 print_count 精确 +2")
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
            assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_idempotency_record WHERE result_data IS NULL "
                    + "AND idempotency_key LIKE 'w5c:%'", Integer.class)).isZero();
        }

        @Test
        @DisplayName("同一防重复请求编号并发重打：只计一次，后到者得到同一份重放结果")
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
                    .isInstanceOfSatisfying(com.xsy.scm.delivery.domain.vo.DeliveryPrintResultVO.class,
                            vo -> assertThat(vo.getOrderCount()).isEqualTo(1)));
        }

        // ---- fixtures ----

        private record Ctx(Long route, Long sku, Long customer, Long a, Long b) {
        }

        private Ctx plannedRoute() {
            Long sku = newOnShelfSku("PRC");
            Long customer = newCustomer();
            // 停靠点坐标来自订单下单时保存的地址，缺失时按「客户地址 == 下单时保存的地址」回落客户坐标，
            // 故必须在下单前把地址写成基类 fixture 的下单时保存的地址，否则坐标为空、规划失败。
            jdbc.update("UPDATE xsy_customer SET address='W5 IT 地址',longitude=113.94,latitude=22.54,geom_crs='GCJ02' WHERE id=?", customer);
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
            var form = new DeliveryRouteForm();
            form.setRouteName("并发打印线路");
            form.setDeliveryDate(LocalDate.now());
            form.setWarehouseId(createPrintWarehouse());
            return delivery.create(form);
        }

        private Long createPrintWarehouse() {
            var warehouse = new WarehouseAddForm();
            warehouse.setName("并发打印仓");
            warehouse.setLongitude(new BigDecimal("113.9"));
            warehouse.setLatitude(new BigDecimal("22.5"));
            warehouse.setGeomCrs("GCJ02");
            Long id = warehouseService.create(warehouse);
            committedWarehouseIds.add(id);
            return id;
        }

        private void add(Long route, List<Long> orderIds) {
            // 组单前把订单做到分拣完成：这是配送候选的硬前置。
            sortingCompletedFor(orderIds.toArray(Long[]::new));
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
            return jdbc.queryForObject("SELECT print_count FROM xsy_delivery_route_order WHERE order_id=?"
                            + " AND assignment_status='ACTIVE' AND deleted=FALSE", Integer.class, orderId);
        }

        /**
         * 子线程没有请求上下文，必须自带身份（{@code ScmOperator.current()} 与防重复处理 scope 都依赖它）。
         *
         * <p>{@code administratorFlag=true} 不是为了「测超管」，而是 {@code mockStatic} 只在创建它的线程生效，
         * 桩不到子线程里的 Sa-Token 判定；本类只测并发计次，线路读取以全范围上下文通过即可。
         * 主线程同样要用这个身份，所以 {@link #useUnrestrictedOperator()} 在基类之后再生成一次。
         */
        private static void setThreadOperator() {
            var employee = new RequestEmployee();
            employee.setEmployeeId(1L);
            employee.setActualName("W5 concurrent IT");
            employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            employee.setAdministratorFlag(true);
            XsyRequestUtil.setRequestUser(employee);
        }

        @BeforeEach
        void useUnrestrictedOperator() {
            setThreadOperator();
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
                    XsyRequestUtil.remove();
                }
            });
        }
    }

    @Nested
    class DeliveryUnitTest {
        @Nested
        @DisplayName("配送读接口权限形状：单域收口，金额靠字段裁剪而非叠权")
        class DeliveryAggregatePermissionTest {
            private static final String ROUTE_QUERY = "scm:delivery:route:query";
            private static final String ROUTE_PRINT = "scm:delivery:route:print";
            private static final String ROUTE_PLAN = "scm:delivery:route:plan";
            private static final String ORDER_QUERY = "scm:order:query";

            /** 端点名 + 应有的那条配送侧权限 + 方法签名（签名变化要同步这里，否则取不到注解）。 */
            private record Endpoint(String name, String permission, Class<?>... parameterTypes) {
            }

            private static final Endpoint LIST = new Endpoint("list", ROUTE_QUERY, DeliveryQueryForm.class);
            private static final Endpoint DETAIL = new Endpoint("detail", ROUTE_QUERY, Long.class);
            private static final Endpoint MAP = new Endpoint("map", ROUTE_QUERY, Long.class);
            private static final Endpoint ORDERS_VIEW = new Endpoint("ordersView", ROUTE_QUERY, Long.class);
            private static final Endpoint CUSTOMERS_VIEW = new Endpoint("customersView", ROUTE_QUERY, Long.class);
            private static final Endpoint CANDIDATES = new Endpoint("candidates", ROUTE_PLAN, DeliveryQueryForm.class);
            private static final Endpoint PRINT = new Endpoint("print", ROUTE_PRINT, Long.class);

            private static String[] permissions(Endpoint endpoint) {
                Method method;
                try {
                    method = DeliveryRouteController.class.getMethod(endpoint.name(), endpoint.parameterTypes());
                } catch (NoSuchMethodException e) {
                    throw new IllegalStateException("配送控制器缺少方法：" + endpoint.name() + "，签名变更需同步本用例", e);
                }
                SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
                assertThat(permission).as("%s 必须声明 @SaCheckPermission", endpoint.name()).isNotNull();
                return permission.value();
            }

            static Stream<Endpoint> readEndpoints() {
                return Stream.of(LIST, DETAIL, MAP, ORDERS_VIEW, CUSTOMERS_VIEW, CANDIDATES, PRINT);
            }

            @ParameterizedTest(name = "{0} 只要求配送侧那一条权限")
            @MethodSource("readEndpoints")
            void gatedByDeliveryPermissionOnly(Endpoint endpoint) {
                assertThat(permissions(endpoint)).containsExactly(endpoint.permission());
            }

            @Test
            @DisplayName("任何配送读出口都不得叠 scm:order:query，否则司机岗被打断")
            void noEndpointDemandsOrderQuery() {
                List<String> all = readEndpoints()
                        .flatMap(endpoint -> Arrays.stream(permissions(endpoint)))
                        .toList();
                assertThat(all).as("金额可见性由 DeliveryVisibility 裁剪，不是叠订单查看权").doesNotContain(ORDER_QUERY);
            }

            @Test
            @DisplayName("候选池要规划权：它是尚未分配的订单加客户地址，司机岗不该拿到")
            void candidatesRequirePlanning() {
                assertThat(permissions(CANDIDATES)).containsExactly(ROUTE_PLAN);
            }
        }

        @Nested

        class DeliveryPlanSnapshotPolicyTest {
            @Test
            void frozenRouteSourceAndOriginalOrderSurviveLaterEdits() {
                var route = new DeliveryRouteEntity();
                route.setVersion(5);
                route.setWarehouseId(8L);
                route.setDeliveryDate(LocalDate.of(2026, 10, 17));
                route.setStatus("PENDING");
                route.setStartLongitude(new BigDecimal("108.940000"));
                route.setStartLatitude(new BigDecimal("34.340000"));
                route.setStartGeomCrs("GCJ02");

                var first = stop(91L, 1, "旧址甲", "108.95", "34.35");
                var second = stop(92L, 2, "旧址乙", "108.96", "34.36");
                var proposal = new DeliveryPlanProposalEntity();
                proposal.setInputSnapshot(DeliveryPlanSnapshotPolicy.capture(route, List.of(first, second)));
                proposal.setResultSnapshot(Map.of("legs", List.of(
                        Map.of("seq", 1, "stopId", 92L, "legDistance", "100.0000", "cumulativeDistance", "100.0000"),
                        Map.of("seq", 2, "stopId", 91L, "legDistance", "200.0000", "cumulativeDistance", "300.0000"))));

                route.setVersion(6);
                route.setStartLongitude(new BigDecimal("110.0000"));
                first.setAddressSnapshot("新址甲");
                first.setStopSeq(2);

                var view = new DeliveryPlanProposalVO();
                DeliveryPlanSnapshotPolicy.fillPreviewSource(proposal, view);
                var legs = DeliveryPlanSnapshotPolicy.legs(proposal);
                assertThat(view.getSourceRouteVersion()).isEqualTo(5);
                assertThat(view.getSourceLongitude()).isEqualByComparingTo("108.94");
                assertThat(view.getSourceGeomCrs()).isEqualTo("GCJ02");
                assertThat(view.getIgnoredFactors()).containsExactly("TIME_WINDOW", "VEHICLE_CAPACITY", "TRAFFIC");
                assertThat(legs).hasSize(2);
                assertThat(legs.get(0).getStopId()).isEqualTo(92L);
                assertThat(legs.get(0).getSeq()).isEqualTo(1);
                assertThat(legs.get(0).getOriginalSeq()).isEqualTo(2);
                assertThat(legs.get(1).getOriginalSeq()).isEqualTo(1);
                assertThat(legs.get(1).getAddressSnapshot()).isEqualTo("旧址甲");
            }

            @Test
            void oldProposalWithoutPreviewMetadataFailsClosedOnPreview() {
                var proposal = new DeliveryPlanProposalEntity();
                proposal.setInputSnapshot(Map.of("routeVersion", "not-a-number"));
                var view = new DeliveryPlanProposalVO();
                DeliveryPlanSnapshotPolicy.fillPreviewSource(proposal, view);
                assertThat(view.getSourceRouteVersion()).isNull();
                assertThat(view.getIgnoredFactors()).isEmpty();
            }

            private static DeliveryRouteStopEntity stop(Long id, int seq, String address, String lon, String lat) {
                var row = new DeliveryRouteStopEntity();
                row.setId(id);
                row.setVersion(0);
                row.setStopSeq(seq);
                row.setCustomerNameSnapshot(address);
                row.setAddressSnapshot(address);
                row.setLongitude(new BigDecimal(lon));
                row.setLatitude(new BigDecimal(lat));
                row.setGeomCrs("GCJ02");
                return row;
            }
        }
    }

    @Nested
    class DeliveryW5PgIT extends ScmW5PgITBase {
        @Nested

        class DeliveryPrintTrackingIT {
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
                assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_inventory_movement WHERE sku_id=?", Integer.class, ctx.sku())).isZero();
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
                // 未规划线路不可正式生成打印：用一个尚未归属任何线路的新订单挂到待排线线路
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

            /** 同样三张订单的线路，但停在待排线：用于验证移出订单后重新组单的聚合规则。 */
            private Ctx draftRoute() {
                Long sku = newOnShelfSku("PRN");
                Long c1 = newCustomer();
                Long c2 = newCustomer();
                // 停靠点坐标来自订单下单时保存的地址；缺失时按「客户地址 == 下单时保存的地址」回落到客户坐标，
                // 故 customer.address 必须等于基类 fixture 硬编码的订单地址，且坐标在下单前写入。
                jdbc.update("UPDATE xsy_customer SET address='W5 IT 地址',longitude=113.94,latitude=22.54,geom_crs='GCJ02' WHERE id IN (?,?)", c1, c2);
                Long a = confirmedSalesOrder(c1, sku, "1.0000", "1.0000");
                Long b = confirmedSalesOrder(c1, sku, "2.0000", "2.0000");
                Long c = confirmedSalesOrder(c2, sku, "3.0000", "3.0000");
                Long route = route();
                add(route, List.of(a, b, c));
                return new Ctx(route, sku, c1, c2, a, b, c);
            }

            private Long route() {
                var w = new WarehouseAddForm();
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
                return jdbc.queryForObject("SELECT print_count FROM xsy_delivery_route_order WHERE order_id=? AND assignment_status='ACTIVE' AND deleted=FALSE",
                        Integer.class, orderId);
            }
        }

        @Nested

        class DeliveryRouteServiceIT {
            @Autowired
            DeliveryRouteService delivery;
            @Autowired
            DeliveryRouteQueryService deliveryQuery;
            @Autowired
            DeliveryCandidateOrderQueryService candidates;

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
            void groupReorderFreezePrintAndRelease() throws Exception {
                Long sku = newOnShelfSku("DL");
                Long c1 = newCustomer();
                Long c2 = newCustomer();
                // The fixture order address is identical; coordinates must be frozen at order creation.
                jdbc.update("UPDATE xsy_customer SET address='W5 IT 地址',longitude=113.94,latitude=22.54,geom_crs='GCJ02' WHERE id IN (?,?)", c1, c2);
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
                assertThat(objectMapper.writeValueAsString(detail)).contains("113.94");
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
                jdbc.update("UPDATE xsy_customer SET address='新地址',longitude=120,latitude=30 WHERE id=?", c1);
                assertThat(deliveryQuery.detail(id).getStops()).allSatisfy(s -> assertThat(s.getAddressSnapshot()).isEqualTo("W5 IT 地址"));
                var items = deliveryQuery.print(id).getItems();
                assertThat(items).hasSize(3);
                // 逐字段查值而不是只查 JSON 里有没有键名：只断言 contains("\"orderId\":") 时，
                // 列别名写错导致映射成 null 也照样通过（键仍在，只是值为 null），等于没钉住。
                assertThat(items).allSatisfy(item -> {
                    assertThat(item.getId()).isNotNull();
                    assertThat(item.getOrderId()).isNotNull();
                    assertThat(item.getProductNameSnapshot()).isNotBlank();
                    assertThat(item.getSaleUnitSnapshot()).isNotBlank();
                    assertThat(item.getOrderedQuantity()).isNotNull();
                });
                // 出网明细不能带 SalesOrderItemEntity 的内部列（33 列里有 deleted/version/createdBy 审计列与
                // draftPriceSourceId/lockedPriceSourceId/manualPriceReason 等价格计算规则字段）。
                var printedItems = objectMapper.writeValueAsString(items);
                // 出网约定：金额为归一到 4 位的定点字符串。注意 JsonConfig 已全局把 BigDecimal 序列化成
                // 字符串，所以这条校验的不是下面的注解，注解额外保证的是「补到 4 位」这层语义。
                assertThat(printedItems).as("定点金额必须是四位小数字符串")
                        .containsPattern("\"orderedQuantity\":\"\\d+\\.\\d{4}\"");
                assertThat(printedItems).doesNotContain("lockedUnitPrice", "manualPriceReason",
                        "draftPriceSourceId", "lockedPriceSourceId", "\"deleted\":", "\"version\":");
                assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_inventory_movement WHERE sku_id=?", Integer.class, sku)).isZero();
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
                // 未分拣完成的订单不是配送候选，组单前先把它们做到 COMPLETED。
                sortingCompletedFor(ids.toArray(Long[]::new));
                var f = new DeliveryOrdersForm();
                f.setVersion(version(id).getVersion());
                f.setOrderIds(ids);
                f.setReason("配送组单");
                delivery.addOrders(id, f);
            }
        }

        @Nested
        @DisplayName("配送数据范围（PG IT）")
        class ScmDeliveryDataScopePgIT {
            /** V43/V55 种下的功能点，必须与 {@code t_menu.api_perms} 逐字一致。 */
            private static final String ROUTE_PLAN = "scm:delivery:route:plan";
            private static final String DELIVERY_ALL = "scm:delivery:scope:all:query";
            private static final String AMOUNT = "scm:delivery:amount:query";
            private static final String WAREHOUSE_ALL = "scm:inventory:scope:all:query";

            /** 基类的下单夹具把收货地址写成这个值；客户地址必须与它完全一致，候选才回落得到客户坐标。 */
            private static final String ADDRESSED = "W5 IT 地址";

            @Autowired
            DeliveryDriverService driverService;
            @Autowired
            DeliveryRouteService routeService;
            @Autowired
            DeliveryRouteQueryService routeQuery;
            @Autowired
            DeliveryCandidateOrderQueryService candidateQuery;

            // ------------------------------------------------------------------
            // 夹具
            // ------------------------------------------------------------------

            /** 直接插一名最小可用员工：范围判定只看 {@code employee_id} 与 {@code deleted_flag}。 */
            private Long newEmployee(String tag) {
                String loginName = (prefix + "-" + tag).toLowerCase(java.util.Locale.ROOT);
                Long departmentId = jdbc.queryForObject("SELECT department_id FROM t_employee ORDER BY employee_id LIMIT 1",
                        Long.class);
                jdbc.update("INSERT INTO t_employee (login_name, login_pwd, actual_name, department_id, administrator_flag)"
                        + " VALUES (?, 'it', ?, ?, FALSE)", loginName, tag, departmentId);
                return jdbc.queryForObject("SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, loginName);
            }

            private void loginAs(Long employeeId) {
                var employee = new RequestEmployee();
                employee.setEmployeeId(employeeId);
                employee.setActualName("范围 IT");
                employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                XsyRequestUtil.setRequestUser(employee);
            }

            /** 司机档案：启用必须带绑定，因此这里只在给出员工时启用。 */
            private Long newDriver(String tag, Long employeeId, String status) {
                var form = new DeliveryDriverForm();
                form.setDriverCode(prefix + "-" + tag);
                form.setDriverName("司机" + tag);
                form.setPhone("13800000000");
                form.setEmployeeId(employeeId);
                form.setStatus(status);
                return driverService.save(form);
            }

            private Long newWarehouse() {
                var warehouse = new WarehouseAddForm();
                warehouse.setName("范围 IT 仓");
                warehouse.setLongitude(new BigDecimal("113.9"));
                warehouse.setLatitude(new BigDecimal("22.5"));
                warehouse.setGeomCrs("GCJ02");
                return warehouseService.create(warehouse);
            }

            private Long newRoute(Long warehouseId, Long driverId) {
                var form = new DeliveryRouteForm();
                form.setRouteName("范围 IT 线路");
                form.setDeliveryDate(LocalDate.now());
                form.setWarehouseId(warehouseId);
                form.setDriverId(driverId);
                return routeService.create(form);
            }

            /** 一个地址与坐标齐备的客户：停靠点坐标来自下单时保存的下单时保存的地址，故必须在下单前改客户地址。 */
            private Long newLocatedCustomer() {
                Long customerId = newCustomer();
                jdbc.update("UPDATE xsy_customer SET address=?,longitude=113.94,latitude=22.54,geom_crs='GCJ02' WHERE id = ?",
                        ADDRESSED, customerId);
                return customerId;
            }

            /** 组单：走写侧路径把订单编进线路，线路版本按原始行回读，不依赖被测的读取门禁。 */
            private Long orderInRoute(Long routeId, Long customerId, Long skuId) {
                return attach(routeId, sortedOrder(customerId, skuId));
            }

            /**
             * 已确认且分拣已完成的订单 —— 这是配送候选的硬前置（P1 裁决第 11 条与补充第 18 条）。
             * 分拣这一步必须在有仓库授权的身份下做，因此需要时与 {@link #attach} 分开调用。
             */
            private Long sortedOrder(Long customerId, Long skuId) {
                Long orderId = confirmedSalesOrder(customerId, skuId, "1.0000", "1.0000");
                sortingCompletedFor(orderId);
                return orderId;
            }

            private Long attach(Long routeId, Long orderId) {
                var form = new DeliveryOrdersForm();
                form.setVersion(versionOf(routeId));
                form.setOrderIds(List.of(orderId));
                form.setReason("组单");
                routeService.addOrders(routeId, form);
                return orderId;
            }

            /** 确认规划：只有 PLANNED 及以后的线路可以出打印单据。 */
            private void plan(Long routeId) {
                var form = new DeliveryVersionForm();
                form.setVersion(versionOf(routeId));
                form.setReason("规划");
                routeService.plan(routeId, form);
            }

            private Integer versionOf(Long routeId) {
                return jdbc.queryForObject("SELECT version FROM xsy_delivery_route WHERE id = ?", Integer.class, routeId);
            }

            private DeliveryPrintOrdersForm printForm(Long routeId, Long orderId) {
                var form = new DeliveryPrintOrdersForm();
                form.setVersion(versionOf(routeId));
                form.setOrderIds(List.of(orderId));
                return form;
            }

            private String key(String tag) {
                return prefix + ":print:" + tag + ":" + System.nanoTime();
            }

            private List<Long> routeIds(List<DeliveryRouteVO> rows) {
                return rows.stream().map(DeliveryRouteVO::getId).toList();
            }

            /** 只授予给定功能点，其余一律 false：与 {@code ScmDataScopeService} 的校验失败时拒绝操作取向一致。 */
            private MockedStatic<StpUtil> grant(String... permissions) {
                var granted = List.of(permissions);
                var stp = mockStatic(StpUtil.class);
                stp.when(() -> StpUtil.hasPermission(anyString())).thenAnswer(call -> granted.contains(call.getArgument(0)));
                return stp;
            }

            /**
             * 越权读取必须是「无权访问」，不能是「记录不存在」。
             *
             * <p>抛的是 {@link ScmDataScopeException} 而不是裸 {@code BusinessException}：只有前者会被
             * {@link ScmExceptionHandler} 翻成 30005 信封，信封这一半必须在域内取证，
             * 否则类型改回去也没人发现（与库存侧同一做法）。
             */
            private void assertDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
                assertThatThrownBy(action).isExactlyInstanceOf(ScmDataScopeException.class)
                        .hasMessage(UserErrorCode.NO_PERMISSION.getMsg());
                var envelope = new ScmExceptionHandler().handleDataScope(new ScmDataScopeException());
                assertThat(envelope.getCode()).isEqualTo(UserErrorCode.NO_PERMISSION.getCode());
                assertThat(envelope.getOk()).isFalse();
            }

            // ------------------------------------------------------------------
            // 线路范围
            // ------------------------------------------------------------------

            @Test
            @DisplayName("绑定了司机的员工只看得到自己名下的线路，别人的与未分配的都看不到")
            void employeeSeesOnlyRoutesOfBoundDriver() {
                Long a = newEmployee("A");
                Long b = newEmployee("B");
                Long d1 = newDriver("D1", a, "ENABLED");
                Long d2 = newDriver("D2", b, "ENABLED");
                Long warehouse = newWarehouse();
                Long r1 = newRoute(warehouse, d1);
                Long r2 = newRoute(warehouse, d2);
                Long r3 = newRoute(warehouse, null);

                loginAs(a);
                try (var stp = grant()) {
                    assertThat(routeIds(routeQuery.query(new DeliveryQueryForm()).getList())).containsExactly(r1);
                    assertThat(routeQuery.detail(r1)).isNotNull();
                    assertDenied(() -> routeQuery.detail(r2));
                    // 未分配司机的线路不属于任何司机，普通司机因此也读不到它。
                    assertDenied(() -> routeQuery.detail(r3));
                    assertDenied(() -> routeQuery.orderView(r2));
                    assertDenied(() -> routeQuery.customerView(r2));
                    assertDenied(() -> routeQuery.print(r2));
                }

                loginAs(b);
                try (var stp = grant()) {
                    assertThat(routeIds(routeQuery.query(new DeliveryQueryForm()).getList())).containsExactly(r2);
                }

                // 持「全部配送数据」的调度：三条线路（含未分配）都在范围内。
                // 用 contains 而不是 containsExactly：全量范围按定义要看得到库里别人的线路，
                // 断言「自己的三条（含 driver 为空的 r3）都在」才是本用例的主题；
                // 「普通司机只能看到自己那一条」由上面的 containsExactly 负责，收窄语义没有被削弱。
                loginAs(a);
                try (var stp = grant(DELIVERY_ALL)) {
                    assertThat(routeIds(routeQuery.query(new DeliveryQueryForm()).getList()))
                            .contains(r1, r2, r3);
                    assertThat(routeQuery.detail(r3)).isNotNull();
                }
            }

            @Test
            @DisplayName("没有司机授权的登录人得到空分页，而不是全量线路")
            void unboundCallerGetsEmptyPageNotEverything() {
                Long owner = newEmployee("C");
                Long warehouse = newWarehouse();
                Long route = newRoute(warehouse, newDriver("D3", owner, "ENABLED"));
                Long existing = newRoute(warehouse, null);

                // 既没绑司机也没有全量授权的登录人（例如销售岗）：列表短路成空分页，详情按无权访问拒绝。
                loginAs(newEmployee("D"));
                try (var stp = grant()) {
                    var page = routeQuery.query(new DeliveryQueryForm());
                    assertThat(page.getTotal()).isZero();
                    assertThat(page.getList()).isEmpty();
                    assertDenied(() -> routeQuery.detail(route));
                    // 真的不存在的线路仍是 41100，不能被越权判定吞成 30005。
                    expectCode(() -> routeQuery.detail(existing + 999999L), 41100);
                }
            }

            // ------------------------------------------------------------------
            // 司机绑定
            // ------------------------------------------------------------------

            @Test
            @DisplayName("启用司机前必须绑定员工；停用可以留空，绑定不存在或已删除员工一律拒绝")
            void enabledDriverRequiresBinding() {
                var unbound = new DeliveryDriverForm();
                unbound.setDriverCode(prefix + "-N1");
                unbound.setDriverName("未绑定司机");
                unbound.setPhone("13800000000");
                unbound.setStatus("ENABLED");
                expectCode(() -> driverService.save(unbound), 41113);

                // 停用态允许留空：外部司机与历史行仍可作为资料存在，只是承担不了配送范围判定。
                unbound.setStatus("DISABLED");
                assertThat(driverService.save(unbound)).isNotNull();

                // 指向不存在的员工：关系列不建外键，只能在写入侧挡。
                unbound.setDriverCode(prefix + "-N2");
                unbound.setEmployeeId(999999999L);
                unbound.setStatus("ENABLED");
                expectCode(() -> driverService.save(unbound), 41114);

                // 员工被软删后，新的绑定同样必须失败。
                Long deleted = newEmployee("E");
                jdbc.update("UPDATE t_employee SET deleted_flag = TRUE WHERE employee_id = ?", deleted);
                unbound.setDriverCode(prefix + "-N3");
                unbound.setEmployeeId(deleted);
                expectCode(() -> driverService.save(unbound), 41114);

                unbound.setDriverCode(prefix + "-N4");
                unbound.setEmployeeId(newEmployee("F"));
                assertThatCode(() -> driverService.save(unbound)).doesNotThrowAnyException();
            }

            @Test
            @DisplayName("一个员工绑第二个活动司机：服务给出明确错误码，唯一索引自己也能挡住")
            void oneEmployeeCannotBindTwoActiveDrivers() {
                Long employee = newEmployee("G");
                Long first = newDriver("D4", employee, "ENABLED");

                // 裸 SQL 探针必须排在服务调用之前：服务那一步靠真 INSERT 撞唯一索引，PostgreSQL 会把
                // 整个测试事务置为 aborted，之后连 setSavepoint 都会连带失败（25P02）。
                // 约束不依赖 Java：绕过服务直接写同一张表也必须在库内失败。
                Long unbound = newDriver("D5", null, "DISABLED");
                expectSqlFailure("UPDATE xsy_delivery_driver SET employee_id = ? WHERE id = ?", employee, unbound);
                expectSqlFailure("INSERT INTO xsy_delivery_driver (driver_code, driver_name, phone, status, employee_id)"
                        + " VALUES (?, ?, ?, 'ENABLED', ?)", prefix + "-D6", "绕过服务司机", "13800000002", employee);
                // 探针行随各自的 SAVEPOINT 回滚：留着第二行会让后面按员工反查线路的用例拿到错的角色
                assertThat(jdbc.queryForList("SELECT id FROM xsy_delivery_driver WHERE employee_id = ? AND deleted = FALSE",
                        Long.class, employee)).containsExactly(first);

                var second = new DeliveryDriverForm();
                second.setDriverCode(prefix + "-D7");
                second.setDriverName("重复绑定司机");
                second.setPhone("13800000001");
                second.setEmployeeId(employee);
                second.setStatus("ENABLED");
                expectCode(() -> driverService.save(second), 41115);
            }

            // ------------------------------------------------------------------
            // 候选订单
            // ------------------------------------------------------------------

            @Test
            @DisplayName("候选池要求组单权与授权仓库：普通司机拿不到全量候选")
            void candidatesRequireDispatchRight() {
                Long sku = newOnShelfSku("SCOPE");
                Long customer = newLocatedCustomer();
                Long confirmed = confirmedSalesOrder(customer, sku, "1.0000", "1.0000");
                // 候选池的门槛里有「分拣已完成」这一条（P1 裁决补充第 18 条），前置放在换身份之前做。
                sortingCompletedFor(confirmed);
                loginAs(newEmployee("H"));

                var filter = new DeliveryQueryForm();
                filter.setCustomerId(customer);

                // 只有线路查询身份的司机（无组单权）：连候选池的入口都没有。
                try (var stp = grant()) {
                    assertDenied(() -> candidateQuery.query(filter));
                }

                // 有组单权但一个授权仓库都没有：不给候选行。
                try (var stp = grant(ROUTE_PLAN)) {
                    assertThat(candidateQuery.query(filter).getList()).isEmpty();
                }

                // 组单权 + 仓库范围才看得见待排线订单；地址与电话在调度范围内完整给出。
                try (var stp = grant(ROUTE_PLAN, WAREHOUSE_ALL, AMOUNT)) {
                    assertThat(candidateQuery.query(filter).getList())
                            .singleElement()
                            .satisfies(row -> {
                                assertThat(row.getOrderId()).isEqualTo(confirmed);
                                assertThat(row.getAddress()).isEqualTo(ADDRESSED);
                                assertThat(row.getReceiverPhone()).isNotBlank();
                                assertThat(row.getOrderAmount()).isNotNull();
                            });
                }
            }

            // ------------------------------------------------------------------
            // 金额与地址
            // ------------------------------------------------------------------

            @Test
            @DisplayName("缺金额权限时配送金额一律为 null（不是 0），有权限时才给出数字")
            void amountsAreHiddenWithoutPermission() {
                Long employee = newEmployee("I");
                Long driver = newDriver("D7", employee, "ENABLED");
                Long warehouse = newWarehouse();
                Long route = newRoute(warehouse, driver);
                Long orderId = orderInRoute(route, newLocatedCustomer(), newOnShelfSku("AMT"));
                plan(route);

                loginAs(employee);
                try (var stp = grant()) {
                    var detail = routeQuery.detail(route);
                    assertThat(detail.getRoute().getTotalAmount()).isNull();
                    assertThat(detail.getStops()).allSatisfy(stop -> assertThat(stop.getTotalAmount()).isNull());
                    assertThat(detail.getOrders()).allSatisfy(row -> assertThat(row.getOrderAmountSnapshot()).isNull());
                    assertThat(routeQuery.orderView(route)).singleElement()
                            .satisfies(row -> assertThat(row.getOrderAmount()).isNull());
                    assertThat(routeQuery.customerView(route)).singleElement()
                            .satisfies(row -> assertThat(row.getTotalAmount()).isNull());
                    // 打印预览与正式生成里的金额同属一个规则，否则一条打印就绕过了字段级收口。
                    var print = routeQuery.print(route);
                    assertThat(print.getDetail().getRoute().getTotalAmount()).isNull();
                    print.getItems().forEach(item -> assertThat(item.getOrderedLineAmount()).isNull());
                    assertThat(routeService.printOrders(route, printForm(route, orderId), key("hidden")).getTotalAmount())
                            .isNull();
                }

                try (var stp = grant(AMOUNT)) {
                    var detail = routeQuery.detail(route);
                    assertThat(detail.getRoute().getTotalAmount()).isNotNull();
                    assertThat(detail.getStops()).allSatisfy(stop -> assertThat(stop.getTotalAmount()).isNotNull());
                    assertThat(routeQuery.orderView(route)).singleElement()
                            .satisfies(row -> assertThat(row.getOrderAmount()).isNotNull());
                    assertThat(routeService.printOrders(route, printForm(route, orderId), key("shown")).getTotalAmount())
                            .isNotNull();
                }
            }

            @Test
            @DisplayName("司机能读自己线路上订单的配送地址与电话，别人线路的整条读取都被拒")
            void driverReadsAddressOnlyInsideOwnRoute() {
                Long own = newEmployee("J");
                Long otherEmployee = newEmployee("K");
                Long warehouse = newWarehouse();
                Long ownRoute = newRoute(warehouse, newDriver("D8", own, "ENABLED"));
                Long otherRoute = newRoute(warehouse, newDriver("D9", otherEmployee, "ENABLED"));
                Long customer = newLocatedCustomer();
                orderInRoute(ownRoute, customer, newOnShelfSku("ADDR"));
                orderInRoute(otherRoute, customer, newOnShelfSku("ADDR2"));

                loginAs(own);
                try (var stp = grant()) {
                    assertThat(routeQuery.orderView(ownRoute)).singleElement().satisfies(row -> {
                        assertThat(row.getAddress()).isEqualTo(ADDRESSED);
                        assertThat(row.getCustomerName()).isNotBlank();
                    });
                    // 停靠点上的收货电话是配送作业必需，随本人线路给出。
                    assertThat(routeQuery.detail(ownRoute).getStops()).allSatisfy(stop -> {
                        assertThat(stop.getAddressSnapshot()).isEqualTo(ADDRESSED);
                        assertThat(stop.getReceiverPhoneSnapshot()).isNotBlank();
                    });
                    assertDenied(() -> routeQuery.orderView(otherRoute));
                }
            }

            // ------------------------------------------------------------------
            // 写侧不受司机维度影响
            // ------------------------------------------------------------------

            @Test
            @DisplayName("写侧的锁定读不受司机维度收窄：主管不会因为司机侧读取限制而失去调度能力")
            void mutationReadsStayUnscoped() {
                Long driverEmployee = newEmployee("L");
                Long warehouse = newWarehouse();
                Long ownRoute = newRoute(warehouse, newDriver("D10", driverEmployee, "ENABLED"));
                Long foreignRoute = newRoute(warehouse, null);

                // 分拣前置放在默认身份下做：司机侧账号没有仓库授权，写不了分拣任务，
                // 而本用例要断的是「组单这条写路径不受司机维度收窄」，不是分拣权。
                Long sortedOrder = sortedOrder(newLocatedCustomer(), newOnShelfSku("WRITE"));
                loginAs(driverEmployee);
                try (var stp = grant()) {
                    // 读侧：未分配司机的线路越权。
                    assertDenied(() -> routeQuery.detail(foreignRoute));
                    // 写侧：组单要锁线路、读候选、重排停靠点，任何一步被司机维度挡掉都会静默废掉调度。
                    attach(foreignRoute, sortedOrder);
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_delivery_route_order WHERE route_id = ?",
                            Integer.class, foreignRoute)).isEqualTo(1);
                    assertThat(routeQuery.detail(ownRoute)).isNotNull();
                }
            }
        }
    }
}
