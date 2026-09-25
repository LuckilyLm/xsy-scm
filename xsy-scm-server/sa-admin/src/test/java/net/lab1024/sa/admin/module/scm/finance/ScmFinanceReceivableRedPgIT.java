package net.lab1024.sa.admin.module.scm.finance;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryOrdersForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryRouteForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliverySignForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryVersionForm;
import net.lab1024.sa.admin.module.scm.delivery.service.DeliveryRouteService;
import net.lab1024.sa.admin.module.scm.finance.service.FinanceReceivableService;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderAddressForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderActualQuantityForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderReturnAddForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderReturnApproveForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderReturnApproveItemForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderReturnItemForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderVersionForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.SalesOrderAddForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.SalesOrderItemForm;
import net.lab1024.sa.admin.module.scm.order.domain.vo.OrderReturnDetailVO;
import net.lab1024.sa.admin.module.scm.order.domain.vo.SalesOrderDetailVO;
import net.lab1024.sa.admin.module.scm.order.domain.vo.SalesOrderItemVO;
import net.lab1024.sa.admin.module.scm.order.service.OrderReturnService;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingActionForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingEntryForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingEntryItemForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingTaskCreateForm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 退货批准 → 红字应收（Finance R1 F1-2C，PG IT）。
 *
 * <p>本类钉的是「红字是已成立退货事实的映射」这一条（第二批 Q27 + 第三批 D-2 / D-4）：
 * <ul>
 *   <li>红字金额一律采用订单域已落库的 {@code approved_amount}，财务不重算；</li>
 *   <li>红字<b>没有任何上限校验</b>：不扣既有核销额、不超过原正常应收、不抛 41137；</li>
 *   <li>「先退后签」在批准时成功跳过、在签收时补生成，且<b>只有一份红字算法</b>；</li>
 *   <li>重复触发（批准重放、签收补生成、生成器重放）都收敛到「一张退货一张红字」。</li>
 * </ul>
 *
 * <p>链路走真实命令（下单 → 分拣 → 发车 → 签收 / 退货建单 → 批准），因为「少拣导致红字大于应收」
 * 这一裁决场景只有真实出库量才能构造出来。
 */
@DisplayName("退货批准生成红字应收（Finance R1 F1-2C，PG IT）")
class ScmFinanceReceivableRedPgIT extends ScmW6PgITBase {

    @Autowired
    private DeliveryRouteService routeService;

    @Autowired
    private OrderReturnService returns;

    @Autowired
    private FinanceReceivableService financeReceivableService;

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private record Chain(Long routeId, Long warehouseId, Long skuId, Long customerId, Long orderId,
                         Long orderItemId) {
    }

    /**
     * 单行链路：已确认订单（可指定单价）+ 已分拣 + 线路已规划，**未发车**。
     *
     * <p>单价走订单手工改价通道（{@code manualPriceOverride}）：这是订单域真实支持的定价路径，
     * 且 0 元赠品行也合法（{@code OrderValidator.decimal(value, false)}），
     * 不需要为了测试去改商品主数据。
     */
    private Chain planned(String tag, String ordered, String price, String sorted) {
        Long warehouseId = locatedWarehouse(tag);
        Long skuId = newOnShelfSku(tag);
        // 库存刻意备足：本类的少拣 / 超额退货场景要的是「出库量与订单量不一致」，
        // 不是「无货可发」，库存不足会让发车失败并把原因混淆成夹具问题。
        stockIn(warehouseId, skuId, tag, "1000.0000");
        Long customerId = addressedCustomer(tag);
        Long orderId = confirmedOrder(tag, customerId, List.of(line(skuId, ordered, price)), ordered);
        sortLines(orderId, List.of(sorted), warehouseId);
        Long routeId = newRoute(warehouseId);
        attach(routeId, orderId);
        locateAllStops(routeId);
        plan(routeId);
        Long orderItemId = confirmedSalesOrderItemId(orderId);
        return new Chain(routeId, warehouseId, skuId, customerId, orderId, orderItemId);
    }

    /** 两行链路（两个 SKU 各自单价），两行都按同一实发量分拣。 */
    private Chain plannedTwoLines(String tag, Long skuA, String priceA, Long skuB, String priceB,
                                  String ordered, String sorted) {
        Long warehouseId = locatedWarehouse(tag);
        stockIn(warehouseId, skuA, tag + "A", "1000.0000");
        stockIn(warehouseId, skuB, tag + "B", "1000.0000");
        Long customerId = addressedCustomer(tag);
        Long orderId = confirmedOrder(tag, customerId,
                List.of(line(skuA, ordered, priceA), line(skuB, ordered, priceB)), ordered);
        sortLines(orderId, List.of(sorted, sorted), warehouseId);
        Long routeId = newRoute(warehouseId);
        attach(routeId, orderId);
        locateAllStops(routeId);
        plan(routeId);
        List<Long> itemIds = orderItemIds(orderId);
        return new Chain(routeId, warehouseId, skuA, customerId, orderId, itemIds.getFirst());
    }

    private SalesOrderItemForm line(Long skuId, String quantity, String price) {
        var item = new SalesOrderItemForm();
        item.setSkuId(skuId);
        item.setOrderedQuantity(quantity);
        item.setManualPriceOverride(true);
        item.setUnitPrice(price);
        item.setOverrideReason("F1-2C IT 协议价");
        return item;
    }

    private List<Long> orderItemIds(Long orderId) {
        return jdbc.queryForList(
                "SELECT id FROM sales_order_item WHERE order_id = ? AND deleted = FALSE ORDER BY id",
                Long.class, orderId);
    }

    /** 已确认订单：下单 → 提交 → 逐行实数量 → 确认（非标品的 confirm 前置）。 */
    private Long confirmedOrder(String tag, Long customerId, List<SalesOrderItemForm> lines,
                                String actualQuantity) {
        var form = new SalesOrderAddForm();
        form.setCustomerId(customerId);
        form.setOrderSource("ADMIN");
        form.setRemark("F1-2C IT 订单");
        var address = new OrderAddressForm();
        address.setReceiverName("F1-2C IT");
        address.setReceiverPhone("13800000000");
        address.setAddress("F1-2C IT 地址");
        form.setAddress(address);
        form.setItems(new ArrayList<>(lines));

        var scope = prefix + ":" + tag + ":so";
        SalesOrderDetailVO order = salesOrderService.create(form, scope + ":create");
        order = salesOrderService.submit(versionOf(order), scope + ":submit");
        for (SalesOrderItemVO item : order.getItems()) {
            var actual = new OrderActualQuantityForm();
            actual.setOrderId(order.getOrderId());
            actual.setItemId(item.getItemId());
            actual.setVersion(item.getVersion());
            actual.setActualQuantity(actualQuantity);
            actual.setReason("F1-2C IT 实重");
            order = salesOrderService.actualQuantity(actual, scope + ":actual:" + item.getItemId());
        }
        Long orderId = salesOrderService.confirm(versionOf(order), scope + ":confirm").getOrderId();
        evictMybatisCache();
        return orderId;
    }

    private OrderVersionForm versionOf(SalesOrderDetailVO order) {
        var form = new OrderVersionForm();
        form.setOrderId(order.getOrderId());
        form.setVersion(order.getVersion());
        return form;
    }

    /** 建单并批准退货：一次请求一行数量、一次批准一行数量（0 批准量是合法的「这行不退」）。 */
    private OrderReturnDetailVO approvedReturn(String tag, Long orderId, List<Long> orderItemIds,
                                               List<String> requested, List<String> approved) {
        var create = new OrderReturnAddForm();
        create.setOrderId(orderId);
        create.setReason("品质问题 " + tag);
        var rows = new ArrayList<OrderReturnItemForm>();
        for (int index = 0; index < orderItemIds.size(); index++) {
            var row = new OrderReturnItemForm();
            row.setOrderItemId(orderItemIds.get(index));
            row.setRequestedQuantity(requested.get(index));
            rows.add(row);
        }
        create.setItems(rows);
        OrderReturnDetailVO created = returns.create(create, key("return-create:" + tag));

        var approve = new OrderReturnApproveForm();
        approve.setReturnId(created.getReturnId());
        approve.setVersion(created.getVersion());
        var approvedRows = new ArrayList<OrderReturnApproveItemForm>();
        for (int index = 0; index < orderItemIds.size(); index++) {
            var row = new OrderReturnApproveItemForm();
            row.setOrderItemId(orderItemIds.get(index));
            row.setApprovedQuantity(approved.get(index));
            approvedRows.add(row);
        }
        approve.setItems(approvedRows);
        return returns.approve(approve, key("return-approve:" + tag));
    }

    private Long addressedCustomer(String tag) {
        Long customerId = newCustomer();
        jdbc.update("UPDATE customer SET address = ?, longitude = 113.94, latitude = 22.54,"
                + " geom_crs = 'GCJ02' WHERE id = ?", "F1-2C IT 地址" + tag, customerId);
        evictMybatisCache();
        return customerId;
    }

    private Long locatedWarehouse(String tag) {
        Long warehouseId = newWarehouse(tag);
        jdbc.update("UPDATE warehouse SET longitude = 113.90, latitude = 22.50, geom_crs = 'GCJ02' WHERE id = ?",
                warehouseId);
        evictMybatisCache();
        return warehouseId;
    }

    private void stockIn(Long warehouseId, Long skuId, String tag, String quantity) {
        Long supplierId = newPurchasableSupplier(tag, skuId);
        PurchaseOrderVO order = purchaseOrderService.create(
                orderForm(supplierId, warehouseId, skuId, quantity, "6.2000"), prefix + ":" + tag + ":po");
        submitOrder(order.getId());
        confirmReceipt(createReceipt(order.getId()).getId(), quantity);
        evictMybatisCache();
    }

    /** 一个任务覆盖全部活动行并录入实发量后完成（配送资格硬前置：每条有效行都被已完成任务覆盖）。 */
    private void sortLines(Long orderId, List<String> sortedQuantities, Long warehouseId) {
        List<Long> itemIds = orderItemIds(orderId);
        var create = new SortingTaskCreateForm();
        create.setWarehouseId(warehouseId);
        create.setAssigneeEmployeeId(1L);
        create.setRemark("F1-2C 前置夹具");
        create.setSalesOrderItemIds(new ArrayList<>(itemIds));
        var detail = sortingTaskService.create(create, key("sort"));

        var entries = new ArrayList<SortingEntryItemForm>();
        for (int index = 0; index < detail.getItems().size(); index++) {
            var row = detail.getItems().get(index);
            var entry = new SortingEntryItemForm();
            entry.setId(row.getId());
            entry.setVersion(row.getVersion());
            entry.setSortedQuantity(new BigDecimal(sortedQuantities.get(index)));
            entry.setResult("NORMAL");
            entries.add(entry);
        }
        var entryForm = new SortingEntryForm();
        entryForm.setItems(entries);
        Long taskId = detail.getTask().getId();
        sortingTaskService.enter(taskId, entryForm);
        var action = new SortingActionForm();
        action.setVersion(jdbc.queryForObject("SELECT version FROM sorting_task WHERE id = ?",
                Integer.class, taskId));
        sortingTaskService.complete(taskId, action);
        evictMybatisCache();
    }

    private Long newRoute(Long warehouseId) {
        var form = new DeliveryRouteForm();
        form.setRouteName("F1-2C IT 线路");
        form.setDeliveryDate(LocalDate.now());
        form.setWarehouseId(warehouseId);
        return routeService.create(form);
    }

    private void attach(Long routeId, Long orderId) {
        var form = new DeliveryOrdersForm();
        form.setVersion(versionOf(routeId));
        form.setOrderIds(List.of(orderId));
        form.setReason("组单");
        routeService.addOrders(routeId, form);
    }

    private void locateAllStops(Long routeId) {
        jdbc.update("UPDATE delivery_route_stop SET longitude = 113.95, latitude = 22.55, geom_crs = 'GCJ02'"
                + " WHERE route_id = ? AND deleted = FALSE", routeId);
        evictMybatisCache();
    }

    private void plan(Long routeId) {
        routeService.plan(routeId, versionForm(routeId));
    }

    private void dispatch(Long routeId) {
        routeService.dispatch(routeId, versionForm(routeId), key("dispatch"));
    }

    private void sign(Long routeId, Long orderId, String result, String reason) {
        var form = new DeliverySignForm();
        form.setVersion(jdbc.queryForObject(
                "SELECT version FROM delivery_route_order WHERE route_id = ? AND order_id = ? AND deleted = FALSE",
                Integer.class, routeId, orderId));
        form.setResult(result);
        form.setReason(reason);
        routeService.sign(routeId, orderId, form);
        evictMybatisCache();
    }

    private DeliveryVersionForm versionForm(Long routeId) {
        var form = new DeliveryVersionForm();
        form.setVersion(versionOf(routeId));
        return form;
    }

    private Integer versionOf(Long routeId) {
        return jdbc.queryForObject("SELECT version FROM delivery_route WHERE id = ?", Integer.class, routeId);
    }

    private String key(String tag) {
        return prefix + ":" + tag + ":" + UUID.randomUUID();
    }

    private Long assignmentId(Long routeId, Long orderId) {
        return jdbc.queryForObject(
                "SELECT id FROM delivery_route_order WHERE route_id = ? AND order_id = ? AND deleted = FALSE",
                Long.class, routeId, orderId);
    }

    // ------------------------------------------------------------------
    // 断言辅助
    // ------------------------------------------------------------------

    private Map<String, Object> normalOf(Long orderId) {
        return single("SELECT * FROM finance_receivable WHERE source_type = 'SALES_ORDER'"
                + " AND entry_type = 'NORMAL' AND source_id = ?", orderId);
    }

    private Map<String, Object> redOf(Long returnId) {
        return single("SELECT * FROM finance_receivable WHERE source_type = 'ORDER_RETURN'"
                + " AND entry_type = 'RED' AND source_id = ?", returnId);
    }

    private Map<String, Object> single(String sql, Object id) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, id);
        assertThat(rows).as("SQL %s 参数 %s 应恰好一行", sql, id).hasSize(1);
        return rows.getFirst();
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private int redsOfOrder(Long orderId) {
        return count("SELECT count(*) FROM finance_receivable WHERE entry_type = 'RED' AND order_id = ?",
                orderId);
    }

    private List<Map<String, Object>> itemsOf(Long receivableId) {
        return jdbc.queryForList(
                "SELECT * FROM finance_receivable_item WHERE receivable_id = ? ORDER BY id", receivableId);
    }

    private List<Map<String, Object>> logsOf(Long receivableId) {
        return jdbc.queryForList(
                "SELECT * FROM finance_operation_log WHERE business_type = 'RECEIVABLE' AND business_id = ?"
                        + " ORDER BY id", receivableId);
    }

    private boolean sameAs(String expression, Object... args) {
        Boolean result = jdbc.queryForObject("SELECT " + expression, Boolean.class, args);
        return Boolean.TRUE.equals(result);
    }

    private static Long longOf(Map<String, Object> row, String column) {
        return ((Number) row.get(column)).longValue();
    }

    private static BigDecimal decimalOf(Map<String, Object> row, String column) {
        return (BigDecimal) row.get(column);
    }

    // ------------------------------------------------------------------
    // A / E. 先签后退：红字正常生成并挂对原应收
    // ------------------------------------------------------------------

    @Test
    @DisplayName("先签后退：RED 挂原 NORMAL，对方与快照继承原应收，时点与原因继承退货事实")
    void returnApprovedAfterSignGeneratesRedReceivable() {
        Chain chain = planned("RDA", "10.0000", "3.5000", "10.0000");
        dispatch(chain.routeId());
        sign(chain.routeId(), chain.orderId(), "SIGNED", null);
        Map<String, Object> normal = normalOf(chain.orderId());

        OrderReturnDetailVO returned = approvedReturn("RDA", chain.orderId(), List.of(chain.orderItemId()),
                List.of("4.0000"), List.of("4.0000"));

        Map<String, Object> red = redOf(returned.getReturnId());
        assertThat(red.get("entry_type")).isEqualTo("RED");
        assertThat(red.get("source_type")).isEqualTo("ORDER_RETURN");
        assertThat(longOf(red, "source_id")).isEqualTo(returned.getReturnId());
        assertThat(longOf(red, "order_id")).isEqualTo(chain.orderId());
        assertThat(longOf(red, "original_receivable_id")).isEqualTo(longOf(normal, "id"));
        assertThat(longOf(red, "customer_id")).isEqualTo(longOf(normal, "customer_id"));
        assertThat(red.get("customer_name_snapshot")).isEqualTo(normal.get("customer_name_snapshot"));
        assertThat(decimalOf(red, "amount")).as("4 × 3.5").isEqualByComparingTo("14.0000");
        assertThat(red.get("reason")).isEqualTo(jdbc.queryForObject(
                "SELECT reason FROM order_return WHERE id = ?", String.class, returned.getReturnId()));
        assertThat(sameAs("r.event_at = rt.approved_at FROM finance_receivable r"
                        + " JOIN order_return rt ON rt.id = ? WHERE r.id = ?",
                returned.getReturnId(), longOf(red, "id")))
                .as("红字时点必须逐值等于退货批准时刻").isTrue();
        assertThat(sameAs("r.created_by = rt.updated_by FROM finance_receivable r"
                        + " JOIN order_return rt ON rt.id = ? WHERE r.id = ?",
                returned.getReturnId(), longOf(red, "id")))
                .as("红字创建人必须是批准人本人（order_return.updated_by）").isTrue();

        // 原正常应收一字未改（第二批 Q27：不修改原 Receivable）
        assertThat(decimalOf(normalOf(chain.orderId()), "amount"))
                .isEqualByComparingTo(decimalOf(normal, "amount"));
        assertThat(longOf(normalOf(chain.orderId()), "version")).isEqualTo(longOf(normal, "version"));

        Map<String, Object> line = itemsOf(longOf(red, "id")).getFirst();
        assertThat(line.get("source_type")).isEqualTo("ORDER_RETURN_ITEM");

        List<Map<String, Object>> logs = logsOf(longOf(red, "id"));
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().get("operation_type")).isEqualTo("RED_GENERATE");
        assertThat(logs.getFirst().get("before_data")).isNull();
        assertThat(logs.getFirst().get("reason")).isEqualTo(red.get("reason"));
        // 操作人必须追溯到本次批准的人（order_return.updated_by），不是财务侧现取的请求身份
        assertThat(sameAs("l.operator = rt.updated_by FROM finance_operation_log l"
                        + " JOIN order_return rt ON rt.id = ? WHERE l.business_id = ?",
                returned.getReturnId(), longOf(red, "id")))
                .as("红字日志的操作人必须是批准人本人").isTrue();
        assertThat(String.valueOf(logs.getFirst().get("after_data")))
                .contains("originalReceivableId").contains("RED").contains("sourceReturnId");
    }

    // ------------------------------------------------------------------
    // B / L. 先退后签：批准时跳过，签收时补生成（可多张）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("先退后签：批准时零红字，签收后自动补生成；两张退货先于签收也全部补齐")
    void returnApprovedBeforeSignIsBackfilledWhenSigning() {
        Chain chain = planned("RDB", "10.0000", "3.5000", "10.0000");

        // 退货批准发生在签收之前
        OrderReturnDetailVO first = approvedReturn("RDB-1", chain.orderId(), List.of(chain.orderItemId()),
                List.of("4.0000"), List.of("4.0000"));
        OrderReturnDetailVO second = approvedReturn("RDB-2", chain.orderId(), List.of(chain.orderItemId()),
                List.of("6.0000"), List.of("6.0000"));

        // 批准成功、退款单已成立，但财务侧什么都没记（不造孤立红字）
        assertThat(first.getStatus()).isEqualTo("APPROVED");
        assertThat(second.getStatus()).isEqualTo("APPROVED");
        assertThat(count("SELECT count(*) FROM finance_receivable WHERE entry_type = 'RED' AND order_id = ?",
                chain.orderId()))
                .as("没有正常应收时不得生成孤立红字").isZero();
        assertThat(count("SELECT count(*) FROM order_refund WHERE order_id = ?", chain.orderId()))
                .as("退款单是订单域事实，照常两张").isEqualTo(2);

        dispatch(chain.routeId());
        sign(chain.routeId(), chain.orderId(), "SIGNED", null);

        assertThat(redsOfOrder(chain.orderId())).as("签收补生成两张红字").isEqualTo(2);
        Map<String, Object> normal = normalOf(chain.orderId());
        Map<String, Object> redOne = redOf(first.getReturnId());
        Map<String, Object> redTwo = redOf(second.getReturnId());
        assertThat(longOf(redOne, "original_receivable_id")).isEqualTo(longOf(normal, "id"));
        assertThat(longOf(redTwo, "original_receivable_id")).isEqualTo(longOf(normal, "id"));
        assertThat(longOf(redOne, "id")).isNotEqualTo(longOf(redTwo, "id"));
        assertThat(decimalOf(redOne, "amount")).isEqualByComparingTo("14.0000");
        assertThat(decimalOf(redTwo, "amount")).isEqualByComparingTo("21.0000");
        assertThat(logsOf(longOf(redOne, "id"))).hasSize(1);
        assertThat(logsOf(longOf(redTwo, "id"))).hasSize(1);
    }

    // ------------------------------------------------------------------
    // F. 红字金额完全采用订单域已落库的 approved_amount（不重算）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("金额不重算：红字逐值等于 order_return_item.approved_amount，单价与数量另各归一列")
    void redAmountIsTheStoredApprovedAmountNotARecomputation() {
        Chain chain = planned("RDF", "10.0000", "3.5000", "10.0000");
        dispatch(chain.routeId());
        sign(chain.routeId(), chain.orderId(), "SIGNED", null);
        OrderReturnDetailVO returned = approvedReturn("RDF", chain.orderId(), List.of(chain.orderItemId()),
                List.of("4.0000"), List.of("4.0000"));
        Long redId = longOf(redOf(returned.getReturnId()), "id");
        Long returnItemId = jdbc.queryForObject(
                "SELECT id FROM order_return_item WHERE return_id = ?", Long.class, returned.getReturnId());

        // 判别性夹具：把 approved_amount 改成一个「与 quantity × price 不等」的合法值（CHECK 只要求 >= 0），
        // 再删掉本次红字并重跑生成器。重算实现会得到 14.0000，采用落库值得到 9.9900。
        assertThat(jdbc.update("UPDATE order_return_item SET approved_amount = 9.9900 WHERE id = ?",
                returnItemId)).isEqualTo(1);
        jdbc.update("DELETE FROM finance_operation_log WHERE business_type = 'RECEIVABLE' AND business_id = ?",
                redId);
        jdbc.update("DELETE FROM finance_receivable_item WHERE receivable_id = ?", redId);
        assertThat(jdbc.update("DELETE FROM finance_receivable WHERE id = ?", redId)).isEqualTo(1);
        evictMybatisCache();

        financeReceivableService.generateRedOnReturnApproved(returned.getReturnId());

        Map<String, Object> line = itemsOf(longOf(redOf(returned.getReturnId()), "id")).getFirst();
        assertThat(decimalOf(line, "amount")).as("必须取落库的 approved_amount，不是乘出来的 14.0000")
                .isEqualByComparingTo("9.9900");
        assertThat(decimalOf(line, "quantity")).isEqualByComparingTo("4.0000");
        assertThat(decimalOf(line, "unit_price")).isEqualByComparingTo("3.5000");
        assertThat(sameAs("i.amount = ri.approved_amount FROM finance_receivable_item i"
                        + " JOIN order_return_item ri ON ri.id = i.source_id WHERE i.id = ?",
                line.get("id"))).as("红字明细金额与订单域落库金额逐值相同").isTrue();
    }

    // ------------------------------------------------------------------
    // G. 合法的 0 批准行只跳过该行，正金额行照常成行
    // ------------------------------------------------------------------

    @Test
    @DisplayName("两行退货（一行 0 批准量）：0 行不成明细，正金额行正常生成，单头为行之和")
    void zeroApprovedLineIsSkippedPerLine() {
        Long skuA = newOnShelfSku("RDG-A");
        Long skuB = newOnShelfSku("RDG-B");
        Chain chain = plannedTwoLines("RDG", skuA, "3.5000", skuB, "8.0000", "10.0000", "10.0000");
        dispatch(chain.routeId());
        sign(chain.routeId(), chain.orderId(), "SIGNED", null);

        List<Long> itemIds = orderItemIds(chain.orderId());
        OrderReturnDetailVO returned = approvedReturn("RDG", chain.orderId(), itemIds,
                List.of("4.0000", "2.0000"), List.of("4.0000", "0.0000"));

        Map<String, Object> red = redOf(returned.getReturnId());
        List<Map<String, Object>> lines = itemsOf(longOf(red, "id"));
        assertThat(lines).as("0 批准量不成行，只留正金额那一行").hasSize(1);
        assertThat(decimalOf(lines.getFirst(), "quantity")).isEqualByComparingTo("4.0000");
        assertThat(decimalOf(red, "amount")).as("14.0000 + 0").isEqualByComparingTo("14.0000");
        // 行级来源指向那条正金额的退货行；0 批准行不占用来源唯一键
        assertThat(longOf(lines.getFirst(), "source_id")).isEqualTo(jdbc.queryForObject(
                "SELECT id FROM order_return_item WHERE return_id = ? AND approved_quantity > 0",
                Long.class, returned.getReturnId()));
    }

    // ------------------------------------------------------------------
    // H. D-2：已核销不影响红字生成
    // ------------------------------------------------------------------

    @Test
    @DisplayName("D-2：NORMAL 100 已全额核销，退货批准 20 仍生成 RED 20，approve 不被阻塞")
    void writtenOffReceivableDoesNotBlockRedGeneration() {
        Chain chain = planned("RDH", "100.0000", "1.0000", "100.0000");
        dispatch(chain.routeId());
        sign(chain.routeId(), chain.orderId(), "SIGNED", null);
        Map<String, Object> normal = normalOf(chain.orderId());
        assertThat(decimalOf(normal, "amount")).isEqualByComparingTo("100.0000");

        // 受控夹具：F1-4 的核销命令尚未实现，因此按 V65 schema 直接落一条合法核销行
        //（source_type RECEIPT 的来源单在 F1-3 之前不存在，本用例只服务「不扣已核销额」这一判据）。
        jdbc.update("INSERT INTO finance_write_off (write_off_no, source_type, source_id, target_type,"
                        + " target_id, amount, entry_type, written_off_at, operator, created_at, updated_at)"
                        + " VALUES (?, 'RECEIPT', 900001, 'RECEIVABLE', ?, 100.0000, 'NORMAL',"
                        + " now(), '2:1', now(), now())",
                "WO-FIXTURE-" + chain.orderId(), longOf(normal, "id"));
        evictMybatisCache();

        OrderReturnDetailVO returned = approvedReturn("RDH", chain.orderId(), List.of(chain.orderItemId()),
                List.of("20.0000"), List.of("20.0000"));

        Map<String, Object> red = redOf(returned.getReturnId());
        assertThat(decimalOf(red, "amount")).as("已核销 100 不抵扣可生成额度（D-2）")
                .isEqualByComparingTo("20.0000");
        // 财务事实净额可直接用 SQL 读出（读侧派生属 F1-5，本轮不建查询层）
        assertThat(jdbc.queryForObject(
                "SELECT sum(CASE WHEN entry_type = 'NORMAL' THEN amount ELSE -amount END)"
                        + " FROM finance_receivable WHERE order_id = ?", BigDecimal.class, chain.orderId()))
                .as("净应收 = 80").isEqualByComparingTo("80.0000");
        assertThat(returned.getStatus()).isEqualTo("APPROVED");
    }

    // ------------------------------------------------------------------
    // I. D-4：红字大于正常应收也全额生成
    // ------------------------------------------------------------------

    @Test
    @DisplayName("D-4：少拣导致退货批准额 50 大于应收 30 → RED 全额 50，不封顶、不抛 41137")
    void redLargerThanNormalIsGeneratedInFull() {
        // 下单 10、实发 6 → 正常应收 6 × 5.0000 = 30；退货按订单结算量批准 10 × 5.0000 = 50
        Chain chain = planned("RDI", "10.0000", "5.0000", "6.0000");
        dispatch(chain.routeId());
        sign(chain.routeId(), chain.orderId(), "SIGNED", null);
        assertThat(decimalOf(normalOf(chain.orderId()), "amount")).isEqualByComparingTo("30.0000");

        OrderReturnDetailVO returned = approvedReturn("RDI", chain.orderId(), List.of(chain.orderItemId()),
                List.of("10.0000"), List.of("10.0000"));

        Map<String, Object> red = redOf(returned.getReturnId());
        assertThat(decimalOf(red, "amount")).as("超额红字必须全额保留").isEqualByComparingTo("50.0000");
        assertThat(jdbc.queryForObject(
                "SELECT sum(CASE WHEN entry_type = 'NORMAL' THEN amount ELSE -amount END)"
                        + " FROM finance_receivable WHERE order_id = ?", BigDecimal.class, chain.orderId()))
                .as("允许净应收为负（D-4），表达属 F1-5 读侧派生").isEqualByComparingTo("-20.0000");
        // 订单域事实未被财务改动
        assertThat(jdbc.queryForObject(
                "SELECT approved_amount FROM order_return WHERE id = ?", BigDecimal.class,
                returned.getReturnId())).isEqualByComparingTo("50.0000");
        assertThat(jdbc.queryForObject(
                "SELECT approved_quantity FROM order_return_item WHERE return_id = ?", BigDecimal.class,
                returned.getReturnId())).isEqualByComparingTo("10.0000");
    }

    // ------------------------------------------------------------------
    // D. 重放不产生第二张红字 / 第二份日志
    // ------------------------------------------------------------------

    @Test
    @DisplayName("重复触发：同一退货被再生成一次 → 仍是一张红字、一份 RED_GENERATE 日志")
    void replayKeepsSingleRedReceivableAndSingleLog() {
        Chain chain = planned("RDD", "10.0000", "3.5000", "10.0000");
        dispatch(chain.routeId());
        sign(chain.routeId(), chain.orderId(), "SIGNED", null);
        OrderReturnDetailVO returned = approvedReturn("RDD", chain.orderId(), List.of(chain.orderItemId()),
                List.of("4.0000"), List.of("4.0000"));
        Long redId = longOf(redOf(returned.getReturnId()), "id");

        financeReceivableService.generateRedOnReturnApproved(returned.getReturnId());
        // 签收派生整体重放：正常应收与红字都必须收敛回原状，不重复插、不重复留痕
        financeReceivableService.generateOnSign(assignmentId(chain.routeId(), chain.orderId()));

        assertThat(longOf(redOf(returned.getReturnId()), "id")).as("重放不换单不换号").isEqualTo(redId);
        assertThat(count("SELECT count(*) FROM finance_receivable WHERE source_type = 'ORDER_RETURN'"
                + " AND source_id = ?", returned.getReturnId())).isEqualTo(1);
        assertThat(itemsOf(redId)).hasSize(1);
        assertThat(logsOf(redId)).as("红字重放不重复留痕").hasSize(1);
        assertThat(count("SELECT count(*) FROM finance_receivable WHERE source_type = 'SALES_ORDER'"
                + " AND source_id = ?", chain.orderId())).isEqualTo(1);
        assertThat(logsOf(longOf(normalOf(chain.orderId()), "id"))).hasSize(1);
    }

    // ------------------------------------------------------------------
    // K. NORMAL 生成器重放能修复漏掉的红字
    // ------------------------------------------------------------------

    @Test
    @DisplayName("漏账修复：NORMAL 已存在而红字缺失时，重跑 generateOnSign 就把它补回来")
    void signGeneratorReplayRepairsAMissingRed() {
        Chain chain = planned("RDK", "10.0000", "3.5000", "10.0000");
        dispatch(chain.routeId());
        sign(chain.routeId(), chain.orderId(), "SIGNED", null);
        OrderReturnDetailVO returned = approvedReturn("RDK", chain.orderId(), List.of(chain.orderItemId()),
                List.of("4.0000"), List.of("4.0000"));
        Long redId = longOf(redOf(returned.getReturnId()), "id");

        // 构造「红字丢了」的状态（例如那条派生事务被回滚掉，而两个事实都还在）
        jdbc.update("DELETE FROM finance_operation_log WHERE business_type = 'RECEIVABLE' AND business_id = ?",
                redId);
        jdbc.update("DELETE FROM finance_receivable_item WHERE receivable_id = ?", redId);
        assertThat(jdbc.update("DELETE FROM finance_receivable WHERE id = ?", redId)).isEqualTo(1);
        evictMybatisCache();
        assertThat(redsOfOrder(chain.orderId())).isZero();

        financeReceivableService.generateOnSign(assignmentId(chain.routeId(), chain.orderId()));

        assertThat(redsOfOrder(chain.orderId())).as("派生生成器可重放即自然收敛").isEqualTo(1);
        Map<String, Object> red = redOf(returned.getReturnId());
        assertThat(decimalOf(red, "amount")).isEqualByComparingTo("14.0000");
        assertThat(decimalOf(normalOf(chain.orderId()), "amount")).as("正常应收不被改动").
                isEqualByComparingTo("35.0000");
        assertThat(count("SELECT count(*) FROM finance_receivable WHERE source_type = 'SALES_ORDER'"
                + " AND source_id = ?", chain.orderId())).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // M. 异常签收不补红字
    // ------------------------------------------------------------------

    @Test
    @DisplayName("EXCEPTION 签收 + 已有批准退货：不造 NORMAL，因此也不造孤立红字")
    void exceptionSignNeverCreatesNormalNorOrphanRed() {
        Chain chain = planned("RDM", "10.0000", "3.5000", "10.0000");
        dispatch(chain.routeId());
        OrderReturnDetailVO returned = approvedReturn("RDM", chain.orderId(), List.of(chain.orderItemId()),
                List.of("4.0000"), List.of("4.0000"));

        sign(chain.routeId(), chain.orderId(), "EXCEPTION", "客户拒收");

        assertThat(count("SELECT count(*) FROM finance_receivable WHERE order_id = ?", chain.orderId()))
                .as("异常签收不形成应收（第二批 Q6）").isZero();
        assertThat(count("SELECT count(*) FROM finance_receivable WHERE source_type = 'ORDER_RETURN'"
                + " AND source_id = ?", returned.getReturnId()))
                .as("没有正常应收就没有可挂的红字").isZero();
        assertThat(returned.getStatus()).isEqualTo("APPROVED");
    }
}
