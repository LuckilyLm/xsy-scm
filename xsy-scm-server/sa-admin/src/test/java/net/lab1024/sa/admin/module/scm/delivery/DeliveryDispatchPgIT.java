package net.lab1024.sa.admin.module.scm.delivery;

import cn.dev33.satoken.stp.StpUtil;
import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeException;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryDriverForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryOrdersForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryRouteForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliverySignForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryVersionForm;
import net.lab1024.sa.admin.module.scm.delivery.service.DeliveryDriverService;
import net.lab1024.sa.admin.module.scm.delivery.service.DeliveryRouteService;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.scm.sorting.domain.entity.SortingTaskEntity;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingActionForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingEntryForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingEntryItemForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingTaskCreateForm;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static net.lab1024.sa.admin.module.scm.delivery.constant.DeliveryErrorCode.DISPATCH_ROUTE_INELIGIBLE;
import static net.lab1024.sa.admin.module.scm.delivery.constant.DeliveryErrorCode.ROUTE_NOT_ALL_SIGNED;
import static net.lab1024.sa.admin.module.scm.delivery.constant.DeliveryErrorCode.SIGN_REASON_REQUIRED;
import static net.lab1024.sa.admin.module.scm.delivery.constant.DeliveryErrorCode.STATE_INVALID;
import static net.lab1024.sa.admin.module.scm.order.constant.OrderErrorCode.ORDER_IDEMPOTENCY_CONFLICT;
import static net.lab1024.sa.admin.module.scm.order.constant.OrderErrorCode.ORDER_IDEMPOTENCY_KEY_REQUIRED;
import static net.lab1024.sa.admin.module.scm.sorting.constant.SortingErrorCode.OUTBOUND_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * 配送 L3 发车 / 签收 / 完成线路（PG IT）。
 *
 * <p>这里钉的是<b>链路</b>而不是算法（库存侧的账在 {@code InventoryFulfillmentPgIT}）：
 *
 * <ol>
 *   <li>发车只消费分拣实发量，线路状态、出库单、订单在途三者要么一起成，要么一起不成；</li>
 *   <li>整条线路原子发车 —— 线路上任何一单失去资格就整条拒绝，不允许「先发一半」；</li>
 *   <li>同一 Idempotency-Key 重放拿到的是同一张出库单，不重复扣库存；缺键直接拒；</li>
 *   <li>P1 裁决第 21 条的守卫现在成立：发车之后分拣不能重开，反过来重开之后不能发车；</li>
 *   <li>签收是订单级终态，异常必须有原因；线路只有在全部活动订单终态后才能完成；</li>
 *   <li>发车后线路不可逆（取消被拒），PLANNED 之外的状态不能发车；</li>
 *   <li>权限取证一律用 {@code administrator_flag = false} 的账号：没有仓库授权的调度不能发车，
 *       绑定到别的司机的员工不能签不属于自己的线路。</li>
 * </ol>
 *
 * <p>线路与分拣刻意用<b>同一个仓库</b>（种子仓），因为本类要断言的是「扣了哪一仓的货」；
 * 跨仓预留的收口在库存命令侧单独钉。
 */
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
        assertThat(jdbc.queryForObject("SELECT outbound_id FROM delivery_route WHERE id = ?", Long.class, one.routeId()))
                .as("裁决第 3 条那条追溯链的起点必须真的落库").isEqualTo(result.getOutboundId());
        assertThat(jdbc.queryForObject("SELECT dispatched_by FROM delivery_route WHERE id = ?", String.class, one.routeId()))
                .as("发车时点必须成对留下操作人").isNotBlank();
        assertThat(fulfillmentStatus(one.routeId(), one.orderId())).isEqualTo("IN_TRANSIT");
        assertThat(onHand(one.warehouseId(), one.skuId())).as("扣的是分拣实发量 3，不是订单行数量").isEqualByComparingTo("7.0000");
        assertThat(jdbc.queryForObject(
                "SELECT i.sales_order_item_id FROM inventory_outbound_item i WHERE i.outbound_id = ?",
                Long.class, result.getOutboundId())).isEqualTo(one.orderItemId());
    }

    @Test
    @DisplayName("同一 Idempotency-Key 重发只回放一次结果，不重复扣库存")
    void repeatedDispatchWithSameKeyReplaysOriginalResult() {
        Case one = plannedRouteWithOneSortedOrder("l3b", "4.0000");
        String key = key("dispatch-once");
        // 重放要求**载荷逐字相同**（version 也在哈希里），所以这里复用同一个 form ——
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
    }

    @Test
    @DisplayName("发车之后：分拣任务不能重开（P1 裁决第 21 条的守卫）")
    void reopenIsBlockedAfterRealOutbound() {
        Case one = plannedRouteWithOneSortedOrder("l3e", "3.0000");
        routeService.dispatch(one.routeId(), dispatchForm(one.routeId()), key("dispatch"));
        Long taskId = taskOfOrderItem(one.orderItemId());

        expectCode(() -> sortingTaskService.reopen(taskId, actionForm(taskId)), OUTBOUND_EXISTS.getCode());

        assertThat(jdbc.queryForObject("SELECT status FROM sorting_task WHERE id = ?", String.class, taskId))
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
        expectCode(() -> routeService.complete(one.routeId(), versionForm(one.routeId())), ROUTE_NOT_ALL_SIGNED.getCode());

        routeService.sign(one.routeId(), one.secondOrderId(), signForm(one.routeId(), one.secondOrderId(), "SIGNED", null));
        routeService.complete(one.routeId(), versionForm(one.routeId()));

        assertThat(routeStatus(one.routeId())).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT completed_by FROM delivery_route WHERE id = ?", String.class, one.routeId()))
                .isNotBlank();
        assertThat(fulfillmentStatus(one.routeId(), one.orderId())).as("异常签收也是终态").isEqualTo("EXCEPTION");
    }

    @Test
    @DisplayName("状态守卫：DRAFT 不能发车，DISPATCHED 之后不能取消，同一订单不能被签两次")
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
        jdbc.update("UPDATE warehouse SET longitude = 113.90, latitude = 22.50, geom_crs = 'GCJ02' WHERE id = ?",
                warehouseId);
        evictMybatisCache();
        return warehouseId;
    }

    private Long sortedLocatedOrder(String tag, Long skuId, String sortedQuantity, Long warehouseId) {
        Long customerId = newCustomer();
        jdbc.update("UPDATE customer SET address=?,longitude=113.94,latitude=22.54,geom_crs='GCJ02' WHERE id = ?",
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
        return jdbc.queryForObject("SELECT si.task_id FROM sorting_task_item si"
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

    /** 停靠点坐标来自下单时冻结的地址快照，所以地址必须在下单前写好。 */
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
     * 专门验；本类关心的是发车之后发生什么，所以不重复走「客户地址 → 订单快照 → 停靠点」这条
     * 坐标搬运链，直接给停靠点落坐标。
     */
    private void locateAllStops(Long routeId) {
        jdbc.update("UPDATE delivery_route_stop SET longitude = 113.95, latitude = 22.55, geom_crs = 'GCJ02'"
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
        jdbc.update("UPDATE delivery_route SET driver_id = ? WHERE id = ?", driverId, routeId);
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
        form.setVersion(jdbc.queryForObject("SELECT version FROM sorting_task WHERE id = ?", Integer.class, taskId));
        form.setReason("发车 IT 重开");
        return form;
    }

    private Integer versionOf(Long routeId) {
        return jdbc.queryForObject("SELECT version FROM delivery_route WHERE id = ?", Integer.class, routeId);
    }

    private Integer orderVersionOf(Long routeId, Long orderId) {
        return jdbc.queryForObject("SELECT version FROM delivery_route_order WHERE route_id = ? AND order_id = ?",
                Integer.class, routeId, orderId);
    }

    private Long orderItemOf(Long orderId) {
        return confirmedSalesOrderItemId(orderId);
    }

    private String routeStatus(Long routeId) {
        return jdbc.queryForObject("SELECT status FROM delivery_route WHERE id = ?", String.class, routeId);
    }

    private String fulfillmentStatus(Long routeId, Long orderId) {
        return jdbc.queryForObject(
                "SELECT fulfillment_status FROM delivery_route_order WHERE route_id = ? AND order_id = ?",
                String.class, routeId, orderId);
    }

    private int outboundCountForRoute(Long routeId) {
        return jdbc.queryForObject("SELECT count(*) FROM inventory_outbound WHERE source_document_id = ?"
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
        jdbc.update("INSERT INTO employee_warehouse_scope (employee_id, warehouse_id) VALUES (?, ?)",
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
        SmartRequestUtil.setRequestUser(employee);
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
        SmartRequestUtil.setRequestUser(employee);
    }

    private Long currentEmployeeId() {
        return ((RequestEmployee) SmartRequestUtil.getRequestUser()).getEmployeeId();
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
