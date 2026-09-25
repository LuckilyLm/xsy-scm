package net.lab1024.sa.admin.module.scm.finance;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryOrdersForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryRouteForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliverySignForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryVersionForm;
import net.lab1024.sa.admin.module.scm.delivery.service.DeliveryRouteService;
import net.lab1024.sa.admin.module.scm.finance.service.FinanceReceivableService;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingActionForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingEntryForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingEntryItemForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingTaskCreateForm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 应收生成失败时签收**真实回滚**（F1-2B，PG IT，无外层事务）。
 *
 * <p>与 {@link ScmFinancePayableRollbackPgIT} 同一取向：要断言「财务写失败后签收没留下 SIGNED」，
 * 每次 Service 调用必须自己提交或自己回滚；沿用「整包在一个事务里回滚」的夹具形态，
 * 看到的永远只是「什么都没发生」，那是测试基础设施造成的假绿。
 *
 * <p>让财务写入失败的方式与应付侧一致：先占掉
 * {@code uk_finance_receivable_item_source_active} 的来源键，使失败发生在数据库层，
 * 与真实故障（约束、超时）走同一条传播路径 —— DAO 抛 / 生成器抛 → 签收事务整体回滚。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("应收生成失败与签收回滚（F1-2B，PG IT，无外层事务）")
class ScmFinanceReceivableRollbackPgIT extends ScmW6PgITBase {

    @Autowired
    private DeliveryRouteService routeService;

    @Autowired
    private FinanceReceivableService financeReceivableService;

    @Override
    protected void evictMybatisCache() {
        // 无外层事务 → 每次 DAO 调用都是新 session → 一级缓存天然为空
    }

    private record Chain(Long routeId, Long warehouseId, Long skuId, Long orderId) {
    }

    /** 真实链路建到「已发车在途」，返回线路与订单 id。 */
    private Chain dispatchedChain(String tag) {
        Long warehouseId = locatedWarehouse(tag);
        Long skuId = newOnShelfSku(tag);
        stockIn(warehouseId, skuId, tag, "20.0000");
        Long customerId = newCustomer();
        jdbc.update("UPDATE customer SET address = ?, longitude = 113.94, latitude = 22.54,"
                + " geom_crs = 'GCJ02' WHERE id = ?", "F1-2B rollback 地址", customerId);
        Long orderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
        sortOneLine(orderId, "10.0000", warehouseId);

        Long routeId = newRoute(warehouseId);
        attach(routeId, orderId);
        locateAllStops(routeId);
        routeService.plan(routeId, versionForm(routeId));
        routeService.dispatch(routeId, versionForm(routeId), key("dispatch"));
        return new Chain(routeId, warehouseId, skuId, orderId);
    }

    private Long locatedWarehouse(String tag) {
        Long warehouseId = newWarehouse(tag);
        jdbc.update("UPDATE warehouse SET longitude = 113.90, latitude = 22.50, geom_crs = 'GCJ02' WHERE id = ?",
                warehouseId);
        return warehouseId;
    }

    private void stockIn(Long warehouseId, Long skuId, String tag, String quantity) {
        Long supplierId = newPurchasableSupplier(tag, skuId);
        PurchaseOrderVO order = purchaseOrderService.create(
                orderForm(supplierId, warehouseId, skuId, quantity, "6.2000"), prefix + ":" + tag + ":po");
        submitOrder(order.getId());
        confirmReceipt(createReceipt(order.getId()).getId(), quantity);
    }

    private void sortOneLine(Long orderId, String sortedQuantity, Long warehouseId) {
        var create = new SortingTaskCreateForm();
        create.setWarehouseId(warehouseId);
        create.setAssigneeEmployeeId(1L);
        create.setRemark("F1-2B rollback 前置");
        create.setSalesOrderItemIds(new ArrayList<>(List.of(confirmedSalesOrderItemId(orderId))));
        var detail = sortingTaskService.create(create, key("sort"));
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
    }

    private Long newRoute(Long warehouseId) {
        var form = new DeliveryRouteForm();
        form.setRouteName("F1-2B rollback 线路");
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
    }

    private DeliveryVersionForm versionForm(Long routeId) {
        var form = new DeliveryVersionForm();
        form.setVersion(versionOf(routeId));
        return form;
    }

    private Integer versionOf(Long routeId) {
        return jdbc.queryForObject("SELECT version FROM delivery_route WHERE id = ?", Integer.class, routeId);
    }

    private Long assignmentId(Long routeId, Long orderId) {
        return jdbc.queryForObject(
                "SELECT id FROM delivery_route_order WHERE route_id = ? AND order_id = ? AND deleted = FALSE",
                Long.class, routeId, orderId);
    }

    private void sign(Long routeId, Long orderId) {
        var form = new DeliverySignForm();
        form.setVersion(jdbc.queryForObject(
                "SELECT version FROM delivery_route_order WHERE id = ?", Integer.class,
                assignmentId(routeId, orderId)));
        form.setResult("SIGNED");
        routeService.sign(routeId, orderId, form);
    }

    private String key(String tag) {
        return prefix + ":" + tag + ":" + UUID.randomUUID();
    }

    // ------------------------------------------------------------------
    // 1. 财务明细写入失败 → 签收整笔回滚
    // ------------------------------------------------------------------

    @Test
    @DisplayName("出库行来源键被占：sign 抛错 → 仍是 IN_TRANSIT、signed_at/signed_by 为空、无半张应收")
    void financeFailureRollsBackTheWholeSign() {
        Chain chain = dispatchedChain("FRR1");
        Long assignment = assignmentId(chain.routeId(), chain.orderId());
        Long outboundItemId = jdbc.queryForObject(
                "SELECT id FROM inventory_outbound_item WHERE sales_order_id = ? AND deleted = FALSE ORDER BY id LIMIT 1",
                Long.class, chain.orderId());

        // 先占掉这条出库行的应收来源键（模拟「同一出库行已被别的应收单记账」的数据异常）
        jdbc.update("INSERT INTO finance_receivable_item (receivable_id, source_type, source_id,"
                        + " order_item_id, sku_id, sku_name_snapshot, unit_snapshot,"
                        + " quantity, unit_price, amount, created_at, updated_at)"
                        + " VALUES (-1, 'INVENTORY_OUTBOUND_ITEM', ?, ?, ?, '占位', 'kg',"
                        + "         1.0000, 1.0000, 1.0000, now(), now())",
                outboundItemId, confirmedSalesOrderItemId(chain.orderId()), chain.skuId());

        try {
            assertThatThrownBy(() -> sign(chain.routeId(), chain.orderId()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("出库行已挂在别的应收单上");

            // 配送侧回到签收前：状态、时点、签收人、乐观锁版本全部未变
            assertThat(jdbc.queryForObject(
                    "SELECT fulfillment_status FROM delivery_route_order WHERE id = ?",
                    String.class, assignment)).isEqualTo("IN_TRANSIT");
            assertThat(jdbc.queryForObject("SELECT signed_at FROM delivery_route_order WHERE id = ?",
                    Object.class, assignment)).isNull();
            assertThat(jdbc.queryForObject("SELECT signed_by FROM delivery_route_order WHERE id = ?",
                    String.class, assignment)).isNull();
            assertThat(jdbc.queryForObject("SELECT version FROM delivery_route_order WHERE id = ?",
                    Integer.class, assignment)).as("回滚后不留下被消耗过的版本号").isEqualTo(1);

            // 财务侧没有半张单：单头 0 条、真实明细 0 条、日志 0 条
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM finance_receivable WHERE source_type = 'SALES_ORDER' AND source_id = ?",
                    Integer.class, chain.orderId())).isZero();
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM finance_receivable_item WHERE source_id = ? AND receivable_id > 0",
                    Integer.class, outboundItemId)).isZero();
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM finance_operation_log l WHERE l.business_type = 'RECEIVABLE'"
                            + " AND l.business_id IN (SELECT id FROM finance_receivable"
                            + "                       WHERE source_type = 'SALES_ORDER' AND source_id = ?)",
                    Integer.class, chain.orderId())).as("应收未成立 → 不留生成日志").isZero();

            // 库存事实与出库单不因财务失败被改动：本 SKU 的 SALES_OUT 还是发车时那一条
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM inventory_movement WHERE movement_type = 'SALES_OUT'"
                            + " AND sku_id = ? AND deleted = FALSE", Integer.class, chain.skuId()))
                    .as("发车流水不受财务失败影响").isEqualTo(1);
        } finally {
            // 占位行是本用例故意造的假数据：append-only 表没有删除入口，测试负责物理清掉
            jdbc.update("DELETE FROM finance_receivable_item WHERE receivable_id = -1");
            // 本用例新建的仓必须停用：NOT_SUPPORTED 让造数随各自事务提交，而「启用仓库恰好唯一一个」
            // 是订单默认仓库解析（41018）与 ScmInventoryOutboundIT 等既有用例的前置假设。
            // 停用是仓库既有做法（DeliveryPrintConcurrencyIT 同一处理），不是绕过断言。
            disableWarehouse(chain.warehouseId());
        }
    }

    // ------------------------------------------------------------------
    // 2. 生成器不接受「自己开一个事务」
    // ------------------------------------------------------------------

    @Test
    @DisplayName("无外层事务调用生成器：MANDATORY 直接拒绝，绝不会自己提交一张应收")
    void generatorRefusesToRunWithoutTheSignTransaction() {
        assertThatThrownBy(() -> financeReceivableService.generateOnSign(-1L))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM finance_receivable WHERE source_id = -1", Integer.class)).isZero();
    }
}
