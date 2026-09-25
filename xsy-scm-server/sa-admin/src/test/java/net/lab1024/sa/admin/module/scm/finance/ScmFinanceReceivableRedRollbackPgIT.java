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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 红字生成失败时退货批准**真实回滚**（F1-2C，PG IT，无外层事务）。
 *
 * <p>与 {@link ScmFinancePayableRollbackPgIT} 同一条理由：要断言「财务写失败后退货批准没留下
 * APPROVED」，每次 Service 调用必须自己提交或自己回滚，否则看到的永远是「什么都没发生」。
 *
 * <p>本类同时是「财务不反向控制订单域」这条裁决的**边界**证明：D-2 / D-4 禁止的是
 * **金额上限**校验阻塞 {@code approve}（本仓库确实没有任何这类检查），而**不是**
 * 「财务写入失败也不许回滚批准」—— 后者会让退货已批准、红字却永久缺失的账无法成立。
 * 两者是不同的事，这里钉的是后者：写入失败 ⇒ 整笔批准回滚。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("红字生成失败与退货批准回滚（F1-2C，PG IT，无外层事务）")
class ScmFinanceReceivableRedRollbackPgIT extends ScmW6PgITBase {

    @Autowired
    private DeliveryRouteService routeService;

    @Autowired
    private OrderReturnService returns;

    @Override
    protected void evictMybatisCache() {
        // 无外层事务 → 每次 DAO 调用都是新 session → 一级缓存天然为空
    }

    // ------------------------------------------------------------------
    // 夹具（真实链路：入库 → 下单 → 分拣 → 发车 → 签收 → 建退货单）
    // ------------------------------------------------------------------

    private Long locatedWarehouse(String tag) {
        Long warehouseId = newWarehouse(tag);
        jdbc.update("UPDATE warehouse SET longitude = 113.90, latitude = 22.50, geom_crs = 'GCJ02' WHERE id = ?",
                warehouseId);
        return warehouseId;
    }

    private Long addressedCustomer(String tag) {
        Long customerId = newCustomer();
        jdbc.update("UPDATE customer SET address = ?, longitude = 113.94, latitude = 22.54,"
                + " geom_crs = 'GCJ02' WHERE id = ?", "F1-2C rollback 地址" + tag, customerId);
        return customerId;
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
        create.setRemark("F1-2C rollback 前置");
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

    private Integer versionOf(Long routeId) {
        return jdbc.queryForObject("SELECT version FROM delivery_route WHERE id = ?", Integer.class, routeId);
    }

    private DeliveryVersionForm versionForm(Long routeId) {
        var form = new DeliveryVersionForm();
        form.setVersion(versionOf(routeId));
        return form;
    }

    private String key(String tag) {
        return prefix + ":" + tag + ":" + UUID.randomUUID();
    }

    private Map<String, Object> row(String sql, Object... args) {
        return jdbc.queryForMap(sql, args);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    // ------------------------------------------------------------------
    // 1. 红字明细撞来源唯一键 → 整笔退货批准回滚
    // ------------------------------------------------------------------

    @Test
    @DisplayName("退货行来源键被占：approve 抛错 → 退货仍 PENDING、无退款单、无日志、NORMAL 未变")
    void redGenerationFailureRollsBackTheWholeApproval() {
        Long warehouseId = locatedWarehouse("RC1");
        Long skuId = newOnShelfSku("RC1");
        stockIn(warehouseId, skuId, "RC1", "1000.0000");
        Long customerId = addressedCustomer("RC1");
        Long orderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
        Long orderItemId = confirmedSalesOrderItemId(orderId);
        sortOneLine(orderId, "10.0000", warehouseId);

        var routeForm = new DeliveryRouteForm();
        routeForm.setRouteName("F1-2C rollback 线路");
        routeForm.setDeliveryDate(LocalDate.now());
        routeForm.setWarehouseId(warehouseId);
        Long routeId = routeService.create(routeForm);
        var attach = new DeliveryOrdersForm();
        attach.setVersion(versionOf(routeId));
        attach.setOrderIds(List.of(orderId));
        attach.setReason("组单");
        routeService.addOrders(routeId, attach);
        jdbc.update("UPDATE delivery_route_stop SET longitude = 113.95, latitude = 22.55, geom_crs = 'GCJ02'"
                + " WHERE route_id = ? AND deleted = FALSE", routeId);
        routeService.plan(routeId, versionForm(routeId));
        routeService.dispatch(routeId, versionForm(routeId), key("dispatch"));
        var sign = new DeliverySignForm();
        sign.setVersion(jdbc.queryForObject(
                "SELECT version FROM delivery_route_order WHERE route_id = ? AND order_id = ? AND deleted = FALSE",
                Integer.class, routeId, orderId));
        sign.setResult("SIGNED");
        routeService.sign(routeId, orderId, sign);

        // 前置事实：正常应收已成立（红字要挂在它上面）
        Map<String, Object> normal = row(
                "SELECT * FROM finance_receivable WHERE source_type = 'SALES_ORDER' AND source_id = ?", orderId);
        Long normalId = ((Number) normal.get("id")).longValue();

        var create = new OrderReturnAddForm();
        create.setOrderId(orderId);
        create.setReason("品质问题 rollback");
        var returnLine = new OrderReturnItemForm();
        returnLine.setOrderItemId(orderItemId);
        returnLine.setRequestedQuantity("4.0000");
        create.setItems(List.of(returnLine));
        OrderReturnDetailVO created = returns.create(create, key("return-create"));
        Long returnItemId = jdbc.queryForObject(
                "SELECT id FROM order_return_item WHERE return_id = ?", Long.class, created.getReturnId());

        // 先占掉这条退货行的红字来源键（模拟「同一退货行已被别的应收单记账」的数据异常）
        jdbc.update("INSERT INTO finance_receivable_item (receivable_id, source_type, source_id,"
                        + " order_item_id, sku_id, sku_name_snapshot, unit_snapshot,"
                        + " quantity, unit_price, amount, created_at, updated_at)"
                        + " VALUES (-1, 'ORDER_RETURN_ITEM', ?, ?, ?, '占位', 'kg', 1.0000, 1.0000, 1.0000,"
                        + " now(), now())",
                returnItemId, orderItemId, skuId);

        try {
            var approve = new OrderReturnApproveForm();
            approve.setReturnId(created.getReturnId());
            approve.setVersion(created.getVersion());
            var approvedLine = new OrderReturnApproveItemForm();
            approvedLine.setOrderItemId(orderItemId);
            approvedLine.setApprovedQuantity("4.0000");
            approve.setItems(List.of(approvedLine));

            assertThatThrownBy(() -> returns.approve(approve, key("return-approve")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("退货行已挂在别的应收单上");

            // 订单域回到批准前：状态、时点、批准量与金额、退款单、操作日志全部未落库
            Map<String, Object> returnRow = row("SELECT * FROM order_return WHERE id = ?",
                    created.getReturnId());
            assertThat(returnRow.get("status")).isEqualTo("PENDING");
            assertThat(returnRow.get("approved_at")).isNull();
            assertThat((BigDecimal) returnRow.get("approved_amount")).isEqualByComparingTo("0.0000");
            assertThat(row("SELECT approved_quantity, approved_amount FROM order_return_item WHERE id = ?",
                    returnItemId).get("approved_quantity")).isNull();
            assertThat(count("SELECT count(*) FROM order_refund WHERE return_id = ?", created.getReturnId()))
                    .as("退款单与批准同事务，必须一起回滚").isZero();
            assertThat(count("SELECT count(*) FROM order_operation_log WHERE order_id = ?"
                    + " AND operation_type = 'RETURN'", orderId))
                    .as("只剩建单那一条日志，批准那条随事务回滚").isEqualTo(1);

            // 财务侧：没有半张红字、没有红字明细、没有红字日志；正常应收一字未改
            assertThat(count("SELECT count(*) FROM finance_receivable WHERE source_type = 'ORDER_RETURN'"
                    + " AND source_id = ?", created.getReturnId())).isZero();
            assertThat(count("SELECT count(*) FROM finance_receivable_item WHERE source_type ="
                    + " 'ORDER_RETURN_ITEM' AND source_id = ? AND receivable_id > 0", returnItemId)).isZero();
            assertThat(count("SELECT count(*) FROM finance_operation_log WHERE business_type ="
                    + " 'RECEIVABLE' AND operation_type = 'RED_GENERATE' AND business_id IN"
                    + " (SELECT id FROM finance_receivable WHERE order_id = ?)", orderId)).isZero();
            Map<String, Object> normalAfter = row(
                    "SELECT * FROM finance_receivable WHERE id = ?", normalId);
            assertThat(normalAfter.get("amount")).isEqualTo(normal.get("amount"));
            assertThat(normalAfter.get("version")).isEqualTo(normal.get("version"));
            assertThat(count("SELECT count(*) FROM finance_receivable_item WHERE receivable_id = ?", normalId))
                    .isEqualTo(1);

            // 签收与出库事实不受影响
            assertThat(row("SELECT fulfillment_status FROM delivery_route_order WHERE route_id = ?"
                            + " AND order_id = ? AND deleted = FALSE", routeId, orderId)
                    .get("fulfillment_status")).isEqualTo("SIGNED");
            assertThat(count("SELECT count(*) FROM inventory_movement WHERE movement_type = 'SALES_OUT'"
                    + " AND sku_id = ? AND deleted = FALSE", skuId)).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM finance_receivable_item WHERE receivable_id = -1");
            disableWarehouse(warehouseId);
        }
    }
}
