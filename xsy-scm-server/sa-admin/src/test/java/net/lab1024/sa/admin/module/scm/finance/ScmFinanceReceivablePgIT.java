package net.lab1024.sa.admin.module.scm.finance;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryOrdersForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryRouteForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliverySignForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryVersionForm;
import net.lab1024.sa.admin.module.scm.delivery.service.DeliveryRouteService;
import net.lab1024.sa.admin.module.scm.finance.service.FinanceReceivableService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 签收 → 正常应收（Finance R1 F1-2B，PG IT）。
 *
 * <p>本类钉的是「应收事实由签收与出库两条既有事实派生」这一口径：
 * 时点必须**逐值等于** {@code delivery_route_order.signed_at}（不是"接近"），
 * 数量必须**只**来自 {@code inventory_outbound_item.quantity}（不是下单量、不是订单结算量、
 * 不是分拣量），价格必须**只**来自 {@code sales_order_item.locked_unit_price}（不是库存成本）。
 *
 * <p>链路刻意走真实命令（下单 → 分拣 → 组单 → 规划 → 发车 → 签收），因为「少拣」这一形态
 * 只有在真实分拣与出库之后才存在；mock 出来的 {@code inventory_outbound_item} 证明不了任何东西。
 *
 * <p><b>受控夹具的三处直改库</b>（商品市场价、出库行软删、补第二条出库行）都是为了构造
 * 「产品规则允许、但本用例无法用现有入口自然走到」的合法数据形状，
 * 没有一处修改产品规则；每处都就地写明理由。
 *
 * <p><b>签收失败回滚不在本类</b>：见 {@link ScmFinanceReceivableRollbackPgIT}。
 */
@DisplayName("签收生成应收（Finance R1 F1-2B，PG IT）")
class ScmFinanceReceivablePgIT extends ScmW6PgITBase {

    @Autowired
    private DeliveryRouteService routeService;

    @Autowired
    private FinanceReceivableService financeReceivableService;

    // ------------------------------------------------------------------
    // 夹具：下单 → 分拣 → 已规划线路
    // ------------------------------------------------------------------

    /** 一条链路所需的全部 id；{@code outboundItemId} 在发车之后才可取。 */
    private record Chain(Long routeId, Long warehouseId, Long skuId, Long customerId, Long orderId) {
    }

    /**
     * 建一条「地址齐备 + 已确认 + 已分拣 + 线路已规划」的链路，**未发车**。
     *
     * @param orderedQuantity 订单行下单量
     * @param marketPrice     SKU 市场价：订单走真实价格解析链路，因此价格只能通过主数据施加
     * @param sortedQuantity  分拣实发量（&lt; 下单量即为少拣）
     */
    private Chain sortedAndPlanned(String tag, String orderedQuantity, String marketPrice,
                                   String sortedQuantity) {
        Long warehouseId = locatedWarehouse(tag);
        Long skuId = newOnShelfSku(tag);
        withMarketPrice(skuId, marketPrice);
        stockIn(warehouseId, skuId, tag, "20.0000");

        Long customerId = newCustomer();
        jdbc.update("UPDATE customer SET address = ?, longitude = 113.94, latitude = 22.54,"
                + " geom_crs = 'GCJ02' WHERE id = ?", "F1-2B IT 地址" + tag, customerId);
        evictMybatisCache();

        // 下单量 = 实数量（非标品的 confirm 前置），少拣只体现在分拣实发量上
        Long orderId = confirmedSalesOrder(customerId, skuId, orderedQuantity, orderedQuantity);
        sortOneLine(orderId, sortedQuantity, warehouseId);

        Long routeId = newRoute(warehouseId);
        attach(routeId, orderId);
        locateAllStops(routeId);
        plan(routeId);
        return new Chain(routeId, warehouseId, skuId, customerId, orderId);
    }

    /** 建两条订单挂同一线路（分单 / 补单场景）。 */
    private Chain sortedAndPlannedTwoOrders(String tag, String sortedQuantity) {
        Long warehouseId = locatedWarehouse(tag);
        Long skuId = newOnShelfSku(tag);
        stockIn(warehouseId, skuId, tag, "20.0000");
        Long firstOrder = addressedConfirmedOrder(tag + "1", warehouseId, skuId);
        sortOneLine(firstOrder, sortedQuantity, warehouseId);
        Long secondOrder = addressedConfirmedOrder(tag + "2", warehouseId, skuId);
        sortOneLine(secondOrder, sortedQuantity, warehouseId);

        Long routeId = newRoute(warehouseId);
        attach(routeId, firstOrder);
        attach(routeId, secondOrder);
        locateAllStops(routeId);
        plan(routeId);
        return new Chain(routeId, warehouseId, skuId, null, firstOrder);
    }

    private Long addressedConfirmedOrder(String tag, Long warehouseId, Long skuId) {
        Long customerId = newCustomer();
        jdbc.update("UPDATE customer SET address = ?, longitude = 113.94, latitude = 22.54,"
                + " geom_crs = 'GCJ02' WHERE id = ?", "F1-2B IT 地址" + tag, customerId);
        evictMybatisCache();
        Long orderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
        evictMybatisCache();
        return orderId;
    }

    private Long locatedWarehouse(String tag) {
        Long warehouseId = newWarehouse(tag);
        jdbc.update("UPDATE warehouse SET longitude = 113.90, latitude = 22.50, geom_crs = 'GCJ02' WHERE id = ?",
                warehouseId);
        evictMybatisCache();
        return warehouseId;
    }

    /**
     * 改 SKU 市场价，让订单在**真实价格解析链路**上拿到调用方要的单价。
     *
     * <p>不走订单手工改价：那会把 {@code locked_price_source} 变成 OVERRIDE，
     * 而本类要证明的是「财务读的就是订单行那个冻结单价列」，与它来自哪个价格源无关。
     */
    private void withMarketPrice(Long skuId, String marketPrice) {
        jdbc.update("UPDATE product_sku SET market_price = ? WHERE id = ?",
                new BigDecimal(marketPrice), skuId);
        evictMybatisCache();
    }

    /** 真实采购入库链路（库存的唯一合法来源），否则分拣与发车无货可扣。 */
    private void stockIn(Long warehouseId, Long skuId, String tag, String quantity) {
        Long supplierId = newPurchasableSupplier(tag, skuId);
        var order = purchaseOrderService.create(
                orderForm(supplierId, warehouseId, skuId, quantity, "6.2000"), prefix + ":" + tag + ":po");
        submitOrder(order.getId());
        confirmReceipt(createReceipt(order.getId()).getId(), quantity);
        evictMybatisCache();
    }

    private void sortOneLine(Long orderId, String sortedQuantity, Long warehouseId) {
        var create = new SortingTaskCreateForm();
        create.setWarehouseId(warehouseId);
        create.setAssigneeEmployeeId(1L);
        create.setRemark("F1-2B 前置夹具");
        create.setSalesOrderItemIds(new ArrayList<>(List.of(confirmedSalesOrderItemId(orderId))));
        var detail = sortingTaskService.create(create, prefix + ":sort:" + orderId);
        Long taskId = detail.getTask().getId();

        var entry = new SortingEntryItemForm();
        entry.setId(detail.getItems().getFirst().getId());
        entry.setVersion(detail.getItems().getFirst().getVersion());
        entry.setSortedQuantity(new BigDecimal(sortedQuantity));
        entry.setResult("NORMAL");
        var entries = new ArrayList<SortingEntryItemForm>();
        entries.add(entry);
        var entryForm = new SortingEntryForm();
        entryForm.setItems(entries);
        sortingTaskService.enter(taskId, entryForm);
        var action = new SortingActionForm();
        action.setVersion(jdbc.queryForObject("SELECT version FROM sorting_task WHERE id = ?",
                Integer.class, taskId));
        sortingTaskService.complete(taskId, action);
        evictMybatisCache();
    }

    private Long newRoute(Long warehouseId) {
        var form = new DeliveryRouteForm();
        form.setRouteName("F1-2B IT 线路");
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
        form.setVersion(orderVersionOf(routeId, orderId));
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

    private Integer orderVersionOf(Long routeId, Long orderId) {
        return jdbc.queryForObject(
                "SELECT version FROM delivery_route_order WHERE route_id = ? AND order_id = ? AND deleted = FALSE",
                Integer.class, routeId, orderId);
    }

    private String key(String tag) {
        return prefix + ":" + tag + ":" + UUID.randomUUID();
    }

    /** 签收行 id（应收时点与签收人的唯一来源）。 */
    private Long assignmentId(Long routeId, Long orderId) {
        return jdbc.queryForObject(
                "SELECT id FROM delivery_route_order WHERE route_id = ? AND order_id = ? AND deleted = FALSE",
                Long.class, routeId, orderId);
    }

    private Long outboundItemIdOf(Long orderId) {
        return jdbc.queryForObject(
                "SELECT id FROM inventory_outbound_item WHERE sales_order_id = ? AND deleted = FALSE ORDER BY id LIMIT 1",
                Long.class, orderId);
    }

    // ------------------------------------------------------------------
    // 断言辅助
    // ------------------------------------------------------------------

    private Map<String, Object> receivableOf(Long orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM finance_receivable WHERE source_type = 'SALES_ORDER' AND source_id = ?", orderId);
        assertThat(rows).as("订单 %s 的应收单（恰好一张）", orderId).hasSize(1);
        return rows.getFirst();
    }

    private int receivablesOfOrder(Long orderId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM finance_receivable WHERE source_type = 'SALES_ORDER' AND source_id = ?",
                Integer.class, orderId);
    }

    /**
     * 本订单的出库行所产生的应收明细数。
     *
     * <p>刻意按来源作用域而不是全表计数：本仓库的 NOT_SUPPORTED 用例会留下已提交的事实，
     * 「全库只有 N 行」这类断言会随执行顺序忽绿忽红。
     */
    private int receivableItemsOfOrder(Long orderId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM finance_receivable_item WHERE source_type = 'INVENTORY_OUTBOUND_ITEM'"
                        + " AND source_id IN (SELECT id FROM inventory_outbound_item WHERE sales_order_id = ?)",
                Integer.class, orderId);
    }

    private int receivableLogsOfOrder(Long orderId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM finance_operation_log WHERE business_type = 'RECEIVABLE'"
                        + " AND business_id IN (SELECT id FROM finance_receivable"
                        + "                       WHERE source_type = 'SALES_ORDER' AND source_id = ?)",
                Integer.class, orderId);
    }

    private List<Map<String, Object>> receivableItemsOf(Long receivableId) {
        return jdbc.queryForList(
                "SELECT * FROM finance_receivable_item WHERE receivable_id = ? ORDER BY id", receivableId);
    }

    private List<Map<String, Object>> receivableLogsOf(Long receivableId) {
        return jdbc.queryForList(
                "SELECT * FROM finance_operation_log WHERE business_type = 'RECEIVABLE' AND business_id = ?",
                receivableId);
    }

    /**
     * 让 PostgreSQL 自己比较两列 —— 「event_at 等于 signed_at」这句话用 Java 比对时区偏移会骗人，
     * 用 {@code toInstant()} 又只是「时刻相同」的近似断言。
     */
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
    // A. 正常链路：PLANNED → 发车 → SIGNED → NORMAL 应收
    // ------------------------------------------------------------------

    @Test
    @DisplayName("正常链路：签收生成一张 NORMAL 应收，单头与明细字段全部来自既有事实")
    void signGeneratesNormalReceivableWithHeaderAndItems() {
        Chain chain = sortedAndPlanned("FRA", "10.0000", "3.5000", "10.0000");
        dispatch(chain.routeId());
        sign(chain.routeId(), chain.orderId(), "SIGNED", null);

        Map<String, Object> receivable = receivableOf(chain.orderId());
        Long receivableId = longOf(receivable, "id");
        assertThat(receivable.get("entry_type")).isEqualTo("NORMAL");
        assertThat(receivable.get("source_type")).isEqualTo("SALES_ORDER");
        assertThat(longOf(receivable, "source_id")).isEqualTo(chain.orderId());
        assertThat(longOf(receivable, "order_id")).isEqualTo(chain.orderId());
        assertThat(longOf(receivable, "customer_id")).isEqualTo(chain.customerId());
        assertThat(receivable.get("customer_name_snapshot")).isEqualTo(jdbc.queryForObject(
                "SELECT customer_name_snapshot FROM sales_order WHERE id = ?", String.class, chain.orderId()));
        assertThat(receivable.get("original_receivable_id")).isNull();
        assertThat(receivable.get("reason")).isNull();
        assertThat(receivable.get("deleted")).isEqualTo(false);
        assertThat(decimalOf(receivable, "amount")).isEqualByComparingTo("35.0000");
        assertThat(String.valueOf(receivable.get("receivable_no"))).matches("AR\\d{14,}");

        List<Map<String, Object>> items = receivableItemsOf(receivableId);
        assertThat(items).hasSize(1);
        Map<String, Object> line = items.getFirst();
        assertThat(line.get("source_type")).isEqualTo("INVENTORY_OUTBOUND_ITEM");
        assertThat(longOf(line, "source_id")).isEqualTo(outboundItemIdOf(chain.orderId()));
        assertThat(longOf(line, "order_item_id")).isEqualTo(confirmedSalesOrderItemId(chain.orderId()));
        assertThat(longOf(line, "sku_id")).isEqualTo(chain.skuId());
        assertThat(line.get("unit_snapshot")).isEqualTo("kg");
        assertThat(decimalOf(line, "quantity")).isEqualByComparingTo("10.0000");
        assertThat(decimalOf(line, "unit_price")).isEqualByComparingTo("3.5000");
        assertThat(decimalOf(line, "amount")).isEqualByComparingTo("35.0000");

        List<Map<String, Object>> logs = receivableLogsOf(receivableId);
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().get("operation_type")).isEqualTo("GENERATE");
        assertThat(logs.getFirst().get("before_data")).isNull();
        assertThat(String.valueOf(logs.getFirst().get("after_data")))
                .contains("receivableNo").contains("35.0000").contains("NORMAL");
    }

    // ------------------------------------------------------------------
    // B / C. 时点与签收人是同一个数据库事实
    // ------------------------------------------------------------------

    @Test
    @DisplayName("event_at 逐值等于 delivery_route_order.signed_at；created_by 等于 signed_by")
    void eventAtAndOperatorAreTheSignedRowFactsThemselves() {
        Chain chain = sortedAndPlanned("FRB", "10.0000", "3.5000", "10.0000");
        dispatch(chain.routeId());
        sign(chain.routeId(), chain.orderId(), "SIGNED", null);
        Long assignment = assignmentId(chain.routeId(), chain.orderId());
        Long receivableId = longOf(receivableOf(chain.orderId()), "id");

        assertThat(sameAs("r.event_at = ro.signed_at"
                        + " FROM finance_receivable r JOIN delivery_route_order ro ON ro.id = ?"
                        + " WHERE r.id = ?",
                assignment, receivableId))
                .as("应收时点必须逐值等于签收时刻，不是「服务层现在时间」")
                .isTrue();
        assertThat(sameAs("r.created_by = ro.signed_by"
                        + " FROM finance_receivable r JOIN delivery_route_order ro ON ro.id = ?"
                        + " WHERE r.id = ?",
                assignment, receivableId))
                .as("应收创建人必须是签收人本人")
                .isTrue();
        assertThat(sameAs("l.operator = ro.signed_by"
                        + " FROM finance_operation_log l"
                        + " JOIN delivery_route_order ro ON ro.id = ?"
                        + " WHERE l.business_type = 'RECEIVABLE' AND l.business_id = ?",
                assignment, receivableId))
                .as("日志操作人必须与 signed_by 一致")
                .isTrue();
        // 明细的身份列同样取签收人，不另立第二个操作人事实
        assertThat(sameAs("i.created_by = ro.signed_by"
                        + " FROM finance_receivable_item i JOIN delivery_route_order ro ON ro.id = ?"
                        + " WHERE i.receivable_id = ?",
                assignment, receivableId)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT signed_at FROM delivery_route_order WHERE id = ?", java.time.OffsetDateTime.class,
                assignment)).isNotNull();
    }

    // ------------------------------------------------------------------
    // D. 少拣：应收量 = 实际出库量
    // ------------------------------------------------------------------

    @Test
    @DisplayName("少拣：下单 10、实发 7 → 应收数量必须 7；订单量与分拣量都不是应收数量事实")
    void receivableQuantityIsTheOutboundQuantityNotOrderOrSortingQuantity() {
        Chain chain = sortedAndPlanned("FRD", "10.0000", "3.5000", "7.0000");
        dispatch(chain.routeId());
        sign(chain.routeId(), chain.orderId(), "SIGNED", null);

        Long orderItemId = confirmedSalesOrderItemId(chain.orderId());
        // 前置事实：订单侧的下单量与结算量都还是 10，分拣行记的是 7
        assertThat(jdbc.queryForObject(
                "SELECT ordered_quantity FROM sales_order_item WHERE id = ?", BigDecimal.class, orderItemId))
                .isEqualByComparingTo("10.0000");
        assertThat(jdbc.queryForObject(
                "SELECT actual_quantity FROM sales_order_item WHERE id = ?", BigDecimal.class, orderItemId))
                .isEqualByComparingTo("10.0000");
        assertThat(jdbc.queryForObject(
                "SELECT sorted_quantity FROM sorting_task_item WHERE sales_order_item_id = ? AND deleted = FALSE",
                BigDecimal.class, orderItemId)).isEqualByComparingTo("7.0000");

        Map<String, Object> receivable = receivableOf(chain.orderId());
        Map<String, Object> line = receivableItemsOf(longOf(receivable, "id")).getFirst();
        assertThat(decimalOf(line, "quantity")).as("应收量取出库行").isEqualByComparingTo("7.0000");
        assertThat(decimalOf(receivable, "amount")).as("7 × 3.5").isEqualByComparingTo("24.5000");
        // 行级来源锚点是出库行主键，不是订单行 id
        assertThat(longOf(line, "source_id")).isEqualTo(outboundItemIdOf(chain.orderId()));
        assertThat(longOf(line, "order_item_id")).isEqualTo(orderItemId);
    }

    // ------------------------------------------------------------------
    // E. 售价唯一来源是订单行冻结单价
    // ------------------------------------------------------------------

    @Test
    @DisplayName("单价：应收 unit_price 逐值等于 locked_unit_price；库存出库成本 6.2 一分都不用")
    void unitPriceComesFromLockedOrderPriceNotInventoryCost() {
        Chain chain = sortedAndPlanned("FRE", "10.0000", "8.1234", "10.0000");
        dispatch(chain.routeId());
        sign(chain.routeId(), chain.orderId(), "SIGNED", null);
        Long orderItemId = confirmedSalesOrderItemId(chain.orderId());

        Map<String, Object> receivable = receivableOf(chain.orderId());
        Map<String, Object> line = receivableItemsOf(longOf(receivable, "id")).getFirst();
        assertThat(sameAs("i.unit_price = oi.locked_unit_price"
                        + " FROM finance_receivable_item i"
                        + " JOIN finance_receivable r ON r.id = i.receivable_id"
                        + " JOIN sales_order_item oi ON oi.id = i.order_item_id"
                        + " WHERE r.source_id = ? AND i.order_item_id = ?",
                chain.orderId(), orderItemId))
                .as("单价必须逐值等于订单行冻结售价")
                .isTrue();
        assertThat(decimalOf(line, "unit_price")).isEqualByComparingTo("8.1234");
        assertThat(decimalOf(line, "amount")).isEqualByComparingTo("81.2340");
        assertThat(decimalOf(receivable, "amount")).isEqualByComparingTo("81.2340");

        // 反向证明：采购入库成本是 6.2000，若读成本金额只会是 62.0000
        assertThat(jdbc.queryForObject(
                "SELECT unit_cost FROM inventory_movement WHERE sku_id = ? AND movement_type = 'PURCHASE_IN'"
                        + " AND deleted = FALSE", BigDecimal.class, chain.skuId()))
                .isEqualByComparingTo("6.2000");
        assertThat(decimalOf(receivable, "amount")).isNotEqualByComparingTo("62.0000");
        // 也不得读订单结算列：本例里 settlement_line_amount = 10 × 8.1234 = 81.2340 会巧合同值，
        // 因此改判「应收金额来自逐行舍入后的出库行之和」这一结构：明细只有一行且数量是出库量
        assertThat(decimalOf(line, "quantity")).isEqualByComparingTo("10.0000");
    }

    // ------------------------------------------------------------------
    // F. HALF_UP 到 4 位
    // ------------------------------------------------------------------

    @Test
    @DisplayName("金额舍入：3.3333 × 2.2222 = 7.40725926 → 7.4073（HALF_UP，不是截断 7.4072）")
    void itemAmountRoundsHalfUpAtScaleFour() {
        Chain chain = sortedAndPlanned("FRF", "10.0000", "2.2222", "3.3333");
        dispatch(chain.routeId());
        sign(chain.routeId(), chain.orderId(), "SIGNED", null);

        Map<String, Object> receivable = receivableOf(chain.orderId());
        Map<String, Object> line = receivableItemsOf(longOf(receivable, "id")).getFirst();
        assertThat(decimalOf(line, "quantity")).isEqualByComparingTo("3.3333");
        assertThat(decimalOf(line, "amount")).isEqualByComparingTo("7.4073");
        assertThat(decimalOf(receivable, "amount")).as("单头 = 已舍入行之和").isEqualByComparingTo("7.4073");
    }

    // ------------------------------------------------------------------
    // G. 一条订单行多条出库行：逐条成行，不合并
    // ------------------------------------------------------------------

    @Test
    @DisplayName("同一订单行两条出库行 → 两条应收明细，各自锚定自己的出库行，不合并")
    void multipleOutboundLinesOfOneOrderLineStaySeparateItems() {
        Chain chain = sortedAndPlanned("FRG", "10.0000", "3.5000", "6.0000");
        dispatch(chain.routeId());

        // 受控夹具：V63 刻意不为 sales_order_item_id 建唯一索引（一条订单行将来可能再出一行），
        // 因此「同订单行的第二条出库行」是 schema 合法形状，但现有发车入口一次只生成一条。
        Long first = outboundItemIdOf(chain.orderId());
        Long outboundId = jdbc.queryForObject(
                "SELECT outbound_id FROM inventory_outbound_item WHERE id = ?", Long.class, first);
        jdbc.update("INSERT INTO inventory_outbound_item (outbound_id, sku_id, quantity, sales_order_id,"
                        + " sales_order_item_id, version, deleted, created_at, updated_at)"
                        + " SELECT ?, sku_id, 2.0000, sales_order_id, sales_order_item_id, 0, FALSE,"
                        + "        now(), now() FROM inventory_outbound_item WHERE id = ?",
                outboundId, first);
        evictMybatisCache();

        sign(chain.routeId(), chain.orderId(), "SIGNED", null);

        Map<String, Object> receivable = receivableOf(chain.orderId());
        List<Map<String, Object>> items = receivableItemsOf(longOf(receivable, "id"));
        assertThat(items).as("两条出库行 = 两条应收明细").hasSize(2);
        assertThat(items).allSatisfy(line ->
                assertThat(longOf(line, "order_item_id")).isEqualTo(confirmedSalesOrderItemId(chain.orderId())));
        assertThat(items).extracting(line -> longOf(line, "source_id")).doesNotHaveDuplicates();
        assertThat(decimalOf(receivable, "amount")).as("(6 + 2) × 3.5").isEqualByComparingTo("28.0000");
    }

    // ------------------------------------------------------------------
    // H. EXCEPTION 不形成应收
    // ------------------------------------------------------------------

    @Test
    @DisplayName("EXCEPTION 签收：无应收、无明细、无日志；SALES_OUT 事实一字未改")
    void exceptionSignGeneratesNoReceivable() {
        Chain chain = sortedAndPlanned("FRH", "10.0000", "3.5000", "10.0000");
        dispatch(chain.routeId());
        // 全部流水断言一律按本用例新建的 SKU 作用域：本仓库还有 NOT_SUPPORTED 的用例会留下
        // 已提交的真实流水，「全库只有两条」这种写法会随执行顺序忽绿忽红。
        BigDecimal salesOutBefore = jdbc.queryForObject(
                "SELECT quantity FROM inventory_movement WHERE movement_type = 'SALES_OUT'"
                        + " AND sku_id = ? AND deleted = FALSE", BigDecimal.class, chain.skuId());

        sign(chain.routeId(), chain.orderId(), "EXCEPTION", "客户拒收");

        assertThat(fulfillmentOf(assignmentId(chain.routeId(), chain.orderId())))
                .isEqualTo("EXCEPTION");
        assertThat(receivablesOfOrder(chain.orderId())).as("异常签收不形成应收（第二批 Q6）").isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM finance_receivable_item WHERE source_type = 'INVENTORY_OUTBOUND_ITEM'"
                        + " AND source_id IN (SELECT id FROM inventory_outbound_item WHERE sales_order_id = ?)",
                Integer.class, chain.orderId())).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM finance_operation_log l WHERE l.business_type = 'RECEIVABLE'"
                        + " AND l.business_id IN (SELECT id FROM finance_receivable WHERE source_id = ?)",
                Integer.class, chain.orderId())).isZero();
        // 财务不反冲库存：SALES_OUT 还在，数量不变，也没有新增反向流水
        assertThat(jdbc.queryForObject(
                "SELECT quantity FROM inventory_movement WHERE movement_type = 'SALES_OUT'"
                        + " AND sku_id = ? AND deleted = FALSE", BigDecimal.class, chain.skuId()))
                .isEqualByComparingTo(salesOutBefore);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM inventory_movement WHERE sku_id = ? AND deleted = FALSE",
                Integer.class, chain.skuId()))
                .as("本 SKU 只有采购入库与销售出库两条流水").isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // I. 签收成功但无有效出库行 → 成功跳过
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SIGNED 且无有效出库行：签收成功、财务跳过，不造空单头也不造 0 元事实")
    void signedWithoutOutboundLinesSkipsFinanceSuccessfully() {
        Chain chain = sortedAndPlanned("FRI", "10.0000", "3.5000", "10.0000");
        dispatch(chain.routeId());

        // 受控夹具：真实入口走不到「已发车出库、但没有任何有效出库行」这一形状
        // （零实发的线路在发车前置就被拒），而第二批 Q8 要求这条跳过分支被取证，
        // 因此按 schema 允许的方式把出库行软删，不修改任何产品规则。
        // 必须断言恰好软删到一行：软删 0 行时下面所有「财务跳过」的断言都会空转通过，
        // 那等于这条用例根本没被执行。
        assertThat(jdbc.update("UPDATE inventory_outbound_item SET deleted = TRUE WHERE sales_order_id = ?",
                chain.orderId())).as("夹具必须真的造出「零有效出库行」").isEqualTo(1);
        evictMybatisCache();

        sign(chain.routeId(), chain.orderId(), "SIGNED", null);

        assertThat(fulfillmentOf(assignmentId(chain.routeId(), chain.orderId())))
                .as("跳过财务事实不是签收失败的副作用").isEqualTo("SIGNED");
        assertThat(receivablesOfOrder(chain.orderId())).isZero();
        assertThat(receivableItemsOfOrder(chain.orderId()))
                .as("本订单的出库行没有产生任何应收明细").isZero();
        assertThat(receivableLogsOfOrder(chain.orderId()))
                .as("跳过不留下任何生成日志").isZero();

        // 重放同样什么都不产生
        financeReceivableService.generateOnSign(assignmentId(chain.routeId(), chain.orderId()));
        assertThat(receivablesOfOrder(chain.orderId())).isZero();
    }

    // ------------------------------------------------------------------
    // J. 合法 0 元订单 → 跳过
    // ------------------------------------------------------------------

    @Test
    @DisplayName("整单单价为 0：签收成功、财务跳过，不生成 0 元应收与空单头")
    void zeroAmountOrderSkipsFinanceFacts() {
        Chain chain = sortedAndPlanned("FRJ", "10.0000", "0.0000", "10.0000");
        dispatch(chain.routeId());

        sign(chain.routeId(), chain.orderId(), "SIGNED", null);

        assertThat(fulfillmentOf(assignmentId(chain.routeId(), chain.orderId()))).isEqualTo("SIGNED");
        assertThat(receivablesOfOrder(chain.orderId())).as("0 元事实不生成（Q8 同纪律）").isZero();
        assertThat(receivableItemsOfOrder(chain.orderId())).isZero();
    }

    // ------------------------------------------------------------------
    // K. 重复生成只有一张应收
    // ------------------------------------------------------------------

    @Test
    @DisplayName("重复生成：第二次命中来源唯一索引 → 仍是一张应收、一份日志，且不报错")
    void duplicateGenerationKeepsSingleReceivableAndSingleLog() {
        Chain chain = sortedAndPlanned("FRK", "10.0000", "3.5000", "10.0000");
        dispatch(chain.routeId());
        sign(chain.routeId(), chain.orderId(), "SIGNED", null);
        Long assignment = assignmentId(chain.routeId(), chain.orderId());
        Long receivableId = longOf(receivableOf(chain.orderId()), "id");

        financeReceivableService.generateOnSign(assignment);
        financeReceivableService.generateOnSign(assignment);

        Map<String, Object> receivable = receivableOf(chain.orderId());
        assertThat(longOf(receivable, "id")).as("重放不换单也不换号").isEqualTo(receivableId);
        assertThat(receivableItemsOf(receivableId)).hasSize(1);
        assertThat(receivableLogsOf(receivableId)).as("已生成不是新事实，不重复留痕").hasSize(1);
    }

    // ------------------------------------------------------------------
    // L. 两条独立订单各自一张应收（补单按独立 source 处理，第二批 Q7）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("同线路两单：各自生成一张应收，单号不重复，不并成一单")
    void eachSignedOrderGetsItsOwnReceivable() {
        Chain chain = sortedAndPlannedTwoOrders("FRL", "10.0000");
        dispatch(chain.routeId());
        Long firstOrder = chain.orderId();
        Long secondOrder = jdbc.queryForObject(
                "SELECT order_id FROM delivery_route_order WHERE route_id = ? AND deleted = FALSE"
                        + " AND order_id <> ? ORDER BY id", Long.class, chain.routeId(), firstOrder);

        sign(chain.routeId(), firstOrder, "SIGNED", null);
        sign(chain.routeId(), secondOrder, "SIGNED", null);

        Map<String, Object> first = receivableOf(firstOrder);
        Map<String, Object> second = receivableOf(secondOrder);
        assertThat(longOf(first, "id")).isNotEqualTo(longOf(second, "id"));
        assertThat(first.get("receivable_no")).isNotEqualTo(second.get("receivable_no"));
        assertThat(receivablesOfOrder(firstOrder)).isEqualTo(1);
        assertThat(receivablesOfOrder(secondOrder)).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // M. 未签收的分配行不能生成应收
    // ------------------------------------------------------------------

    @Test
    @DisplayName("在途未签收就调用生成器 → 抛错且不产生应收（EXCEPTION 与 SIGNED 共用一条 UPDATE 的防线）")
    void inTransitAssignmentCannotGenerateReceivable() {
        Chain chain = sortedAndPlanned("FRM", "10.0000", "3.5000", "10.0000");
        dispatch(chain.routeId());
        Long assignment = assignmentId(chain.routeId(), chain.orderId());
        assertThat(fulfillmentOf(assignment)).isEqualTo("IN_TRANSIT");

        assertThatThrownBy(() -> financeReceivableService.generateOnSign(assignment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不能生成应收");

        assertThat(receivablesOfOrder(chain.orderId())).isZero();
    }

    private String fulfillmentOf(Long assignment) {
        return jdbc.queryForObject("SELECT fulfillment_status FROM delivery_route_order WHERE id = ?",
                String.class, assignment);
    }
}
