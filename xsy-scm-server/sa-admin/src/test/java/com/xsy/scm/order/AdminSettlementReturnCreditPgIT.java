package com.xsy.scm.order;

import com.xsy.scm.common.ScmW6PgITBase;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.order.service.OrderCreditService;
import com.xsy.scm.inventory.domain.form.InventoryOutboundAddForm;
import com.xsy.scm.inventory.service.InventoryOutboundService;
import com.xsy.scm.order.domain.form.OrderActualQuantityForm;
import com.xsy.scm.order.domain.form.OrderAddressForm;
import com.xsy.scm.order.domain.form.OrderReturnAddForm;
import com.xsy.scm.order.domain.form.OrderReturnApproveForm;
import com.xsy.scm.order.domain.form.OrderReturnApproveItemForm;
import com.xsy.scm.order.domain.form.OrderReturnItemForm;
import com.xsy.scm.order.domain.form.OrderReturnReceiveForm;
import com.xsy.scm.order.domain.form.OrderReturnReceiveItemForm;
import com.xsy.scm.order.domain.form.SalesOrderAddForm;
import com.xsy.scm.order.domain.form.SalesOrderItemForm;
import com.xsy.scm.order.domain.vo.OrderReturnDetailVO;
import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.service.OrderReturnReceiptService;
import com.xsy.scm.order.service.OrderReturnService;
import com.xsy.scm.order.service.SalesOrderQueryService;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminSettlementReturnCreditPgIT extends ScmW6PgITBase {
    @Autowired private OrderReturnService orderReturnService;
    @Autowired private OrderReturnReceiptService orderReturnReceiptService;
    @Autowired private InventoryOutboundService inventoryOutboundService;
    @Autowired private SalesOrderQueryService salesOrderQueryService;
    @Autowired private OrderCreditService orderCreditService;

    private com.xsy.scm.order.domain.form.OrderVersionForm salesOrderVersion(SalesOrderDetailVO order) {
        var form = new com.xsy.scm.order.domain.form.OrderVersionForm();
        form.setOrderId(order.getOrderId());
        form.setVersion(order.getVersion());
        return form;
    }

    private String key() { return UUID.randomUUID().toString(); }

    private record Returned(Long skuId, Long warehouseId, OrderReturnDetailVO returned) { }

    private Returned returned() {
        Long skuId = newOnShelfSku("return");
        W6Fixture fixture = inboundFixture("return", skuId, "10.0000");
        confirmReceipt(fixture.receipt().getId(), "10.0000");
        Long customerId = newCustomer();
        Long orderId = confirmedSalesOrder(customerId, skuId, "5.0000", "5.0000");
        Long orderItemId = confirmedSalesOrderItemId(orderId);
        InventoryOutboundAddForm outbound = new InventoryOutboundAddForm();
        outbound.setWarehouseId(seedWarehouseId());
        InventoryOutboundAddForm.Item outboundItem = new InventoryOutboundAddForm.Item();
        outboundItem.setSkuId(skuId);
        outboundItem.setQuantity(new BigDecimal("5.0000"));
        outbound.setItems(List.of(outboundItem));
        Long outboundId = inventoryOutboundService.create(outbound);
        // Link a real inventory command to its sales source, as the delivery adapter does.
        jdbc.update("UPDATE inventory_outbound_item SET sales_order_id=?, sales_order_item_id=? WHERE outbound_id=?", orderId, orderItemId, outboundId);
        evictMybatisCache();
        inventoryOutboundService.confirm(outboundId);
        OrderReturnItemForm item = new OrderReturnItemForm();
        item.setOrderItemId(orderItemId);
        item.setRequestedQuantity("5.0000");
        OrderReturnAddForm form = new OrderReturnAddForm();
        form.setOrderId(orderId);
        form.setReason("验收退货");
        form.setItems(List.of(item));
        var returned = orderReturnService.create(form, key());
        OrderReturnApproveItemForm approvedItem = new OrderReturnApproveItemForm();
        approvedItem.setOrderItemId(orderItemId);
        approvedItem.setApprovedQuantity("5.0000");
        OrderReturnApproveForm approve = new OrderReturnApproveForm();
        approve.setReturnId(returned.getReturnId());
        approve.setVersion(returned.getVersion());
        approve.setItems(List.of(approvedItem));
        return new Returned(skuId, seedWarehouseId(), orderReturnService.approve(approve, key()));
    }

    private Returned splitCostReturned() {
        Long skuId = newOnShelfSku("return-fifo");
        W6Fixture firstInbound = freeInboundFixture("return-fifo-first", skuId, "2.0000", "kg");
        confirmReceipt(firstInbound.receipt().getId(), "2.0000");

        Long customerId = newCustomer();
        Long orderId = confirmedSalesOrder(customerId, skuId, "5.0000", "5.0000");
        Long orderItemId = confirmedSalesOrderItemId(orderId);
        confirmSalesOutbound(orderId, orderItemId, skuId, "2.0000");

        Long supplierId = newSupplier("return-fifo-second");
        linkSupplierSku(supplierId, skuId, "kg");
        var secondOrder = createDraftOrder("return-fifo-second", supplierId, skuId, "3.0000", "8.4000");
        var secondReceipt = submittedOrderReceipt(secondOrder.getId());
        confirmReceipt(secondReceipt.getId(), "3.0000");
        confirmSalesOutbound(orderId, orderItemId, skuId, "3.0000");

        OrderReturnItemForm item = new OrderReturnItemForm();
        item.setOrderItemId(orderItemId);
        item.setRequestedQuantity("5.0000");
        OrderReturnAddForm form = new OrderReturnAddForm();
        form.setOrderId(orderId);
        form.setReason("FIFO 成本验收退货");
        form.setItems(List.of(item));
        var returned = orderReturnService.create(form, key());
        OrderReturnApproveItemForm approvedItem = new OrderReturnApproveItemForm();
        approvedItem.setOrderItemId(orderItemId);
        approvedItem.setApprovedQuantity("5.0000");
        OrderReturnApproveForm approve = new OrderReturnApproveForm();
        approve.setReturnId(returned.getReturnId());
        approve.setVersion(returned.getVersion());
        approve.setItems(List.of(approvedItem));
        return new Returned(skuId, seedWarehouseId(), orderReturnService.approve(approve, key()));
    }

    private void confirmSalesOutbound(Long orderId, Long orderItemId, Long skuId, String quantity) {
        InventoryOutboundAddForm outbound = new InventoryOutboundAddForm();
        outbound.setWarehouseId(seedWarehouseId());
        InventoryOutboundAddForm.Item outboundItem = new InventoryOutboundAddForm.Item();
        outboundItem.setSkuId(skuId);
        outboundItem.setQuantity(new BigDecimal(quantity));
        outbound.setItems(List.of(outboundItem));
        Long outboundId = inventoryOutboundService.create(outbound);
        jdbc.update("UPDATE inventory_outbound_item SET sales_order_id=?, sales_order_item_id=? WHERE outbound_id=?",
                orderId, orderItemId, outboundId);
        evictMybatisCache();
        inventoryOutboundService.confirm(outboundId);
    }

    private void shadowInventoryMovements() {
        jdbc.execute("CREATE TEMP TABLE movement_shadow (LIKE inventory_movement INCLUDING ALL) ON COMMIT DROP");
        jdbc.execute("INSERT INTO pg_temp.movement_shadow SELECT * FROM inventory_movement");
        jdbc.execute("ALTER TABLE pg_temp.movement_shadow RENAME TO inventory_movement");
        jdbc.execute("SELECT setval(pg_get_serial_sequence('pg_temp.inventory_movement', 'id'), "
                + "COALESCE((SELECT max(id) FROM pg_temp.inventory_movement), 1), "
                + "EXISTS (SELECT 1 FROM pg_temp.inventory_movement))");
        evictMybatisCache();
    }

    private OrderReturnReceiveForm receiveForm(Returned fixture, String quantity, String disposition) {
        OrderReturnReceiveItemForm item = new OrderReturnReceiveItemForm();
        item.setReturnItemId(fixture.returned().getItems().getFirst().getReturnItemId());
        item.setQuantity(quantity);
        item.setDisposition(disposition);
        OrderReturnReceiveForm form = new OrderReturnReceiveForm();
        form.setReturnId(fixture.returned().getReturnId());
        form.setVersion(fixture.returned().getVersion());
        form.setWarehouseId(fixture.warehouseId());
        form.setItems(List.of(item));
        return form;
    }

    @Test void receiptUsesHistoricalCostAndReplayDoesNotDuplicateStock() {
        var fixture = returned();
        var form = receiveForm(fixture, "2.0000", "RETURN_TO_STOCK");
        String key = key();
        var first = orderReturnReceiptService.receive(form, key);
        assertThat(orderReturnReceiptService.receive(form, key).getReceiptId()).isEqualTo(first.getReceiptId());
        assertThat(balanceRow(fixture.warehouseId(), fixture.skuId()).getQuantity()).isEqualByComparingTo("7.0000");
        assertThat(jdbc.queryForObject("SELECT unit_cost FROM order_return_receipt_item WHERE receipt_id=?",
                BigDecimal.class, first.getReceiptId())).isEqualByComparingTo("6.2000");
        orderReturnReceiptService.receive(receiveForm(fixture, "1.0000", "DAMAGE"), key());
        assertThat(balanceRow(fixture.warehouseId(), fixture.skuId()).getQuantity()).isEqualByComparingTo("7.0000");
        assertThat(orderReturnService.detail(fixture.returned().getReturnId()).getItems().getFirst().getReceivedQuantity())
                .isEqualByComparingTo("3.0000");
    }

    @Test void partialReturnSplitsAcrossHistoricalSalesOutCostsInFifoOrder() {
        var fixture = splitCostReturned();
        Long orderItemId = fixture.returned().getItems().getFirst().getOrderItemId();
        var allocations = inventoryMovementDao.listSalesOutAllocations(orderItemId);
        assertThat(allocations).extracting(m -> m.getQuantity().toPlainString())
                .containsExactly("2.0000", "3.0000");
        assertThat(allocations).extracting(m -> m.getUnitCost().toPlainString())
                .containsExactly("6.2000", "8.4000");
        expectSqlFailure("UPDATE inventory_movement SET unit_cost=99 WHERE id=?", allocations.getFirst().getId());

        var result = orderReturnReceiptService.receive(receiveForm(fixture, "4.0000", "RETURN_TO_STOCK"), key());

        assertThat(result.getItems()).extracting(i -> i.getQuantity().toPlainString())
                .containsExactly("2.0000", "2.0000");
        assertThat(jdbc.queryForList("SELECT unit_cost FROM order_return_receipt_item WHERE receipt_id=? ORDER BY id",
                BigDecimal.class, result.getReceiptId())).containsExactly(new BigDecimal("6.2000"), new BigDecimal("8.4000"));
        assertThat(result.getItems()).extracting(i -> i.getSourceSalesOutMovementId())
                .containsExactly(allocations.get(0).getId(), allocations.get(1).getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_movement WHERE movement_type='SALES_RETURN_IN'"
                + " AND source_document_id=?", Integer.class, fixture.returned().getReturnId())).isEqualTo(2);
        assertThat(balanceRow(fixture.warehouseId(), fixture.skuId()).getQuantity()).isEqualByComparingTo("4.0000");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void excessiveReceiptRollsBackHeaderAndStock() {
        var fixture = returned();
        orderReturnReceiptService.receive(receiveForm(fixture, "4.0000", "DAMAGE"), key());
        assertThatThrownBy(() -> orderReturnReceiptService.receive(receiveForm(fixture, "2.0000", "RETURN_TO_STOCK"), key()))
                .isInstanceOf(ScmBusinessException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_return_receipt WHERE return_id=?", Integer.class,
                fixture.returned().getReturnId())).isEqualTo(1);
        assertThat(balanceRow(fixture.warehouseId(), fixture.skuId()).getQuantity()).isEqualByComparingTo("5.0000");
    }

    @Test void replayRechecksOwnerAndWarehousePermissions() {
        var fixture = returned();
        var form = receiveForm(fixture, "1.0000", "DAMAGE");
        String key = key();
        orderReturnReceiptService.receive(form, key);
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(987654321L);
        employee.setAdministratorFlag(false);
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        SmartRequestUtil.setRequestUser(employee);
        assertThatThrownBy(() -> orderReturnReceiptService.receive(form, key)).isInstanceOf(ScmDataScopeException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void staleVersionRejectsNewReceipt() {
        var fixture = returned();
        var form = receiveForm(fixture, "1.0000", "RETURN_TO_STOCK");
        form.setVersion(form.getVersion() + 1);
        expectCode(() -> orderReturnReceiptService.receive(form, key()), 40921);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_return_receipt WHERE return_id=?", Integer.class,
                fixture.returned().getReturnId())).isZero();
    }

    @Test void receiptRequiresWarehouseScopeEvenForDamageAndReplay() {
        var fixture = returned();
        Long otherWarehouse = newWarehouse("return-target");
        var form = receiveForm(fixture, "1.0000", "DAMAGE");
        form.setWarehouseId(otherWarehouse);
        String key = key();
        orderReturnReceiptService.receive(form, key);
        jdbc.update("UPDATE sales_order SET seller_id=1 WHERE id=?", fixture.returned().getOrderId());
        evictMybatisCache();
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setAdministratorFlag(false);
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        SmartRequestUtil.setRequestUser(employee);
        assertThatThrownBy(() -> orderReturnReceiptService.receive(form, key)).isInstanceOf(ScmDataScopeException.class);
        assertThatThrownBy(() -> orderReturnReceiptService.receive(form, key())).isInstanceOf(ScmDataScopeException.class);
    }

    @Test void selectedHistoricalAllocationRequiresFrozenCost() {
        var fixture = returned();
        Long orderItemId = fixture.returned().getItems().getFirst().getOrderItemId();
        Long movementId = inventoryMovementDao.listSalesOutAllocations(orderItemId).getFirst().getId();
        expectSqlFailure("UPDATE inventory_movement SET unit_cost=NULL WHERE id=?", movementId);
        shadowInventoryMovements();
        assertThat(jdbc.update("UPDATE pg_temp.inventory_movement SET unit_cost=NULL WHERE id=?", movementId)).isEqualTo(1);
        evictMybatisCache();
        assertThat(inventoryMovementDao.listSalesOutAllocations(orderItemId)).anyMatch(m -> m.getUnitCost() == null);
        assertThatThrownBy(() -> orderReturnReceiptService.receive(receiveForm(fixture,"1.0000","RETURN_TO_STOCK"),key()))
                .isInstanceOf(ScmBusinessException.class);
    }

    private SalesOrderDetailVO pending(Long customerId, Long skuId, String ordered, String actual) {
        SalesOrderAddForm form = orderForm(customerId, skuId, ordered);
        var order = salesOrderService.create(form, key());
        order = salesOrderService.submit(salesOrderVersion(order), key());
        OrderActualQuantityForm quantity = new OrderActualQuantityForm();
        quantity.setOrderId(order.getOrderId());
        quantity.setItemId(order.getItems().getFirst().getItemId());
        quantity.setVersion(order.getItems().getFirst().getVersion());
        quantity.setActualQuantity(actual);
        quantity.setReason("实际验收数量");
        return salesOrderService.actualQuantity(quantity, key());
    }

    private SalesOrderAddForm orderForm(Long customerId, Long skuId, String quantity) {
        SalesOrderAddForm form = new SalesOrderAddForm();
        form.setCustomerId(customerId);
        form.setOrderSource("ADMIN");
        OrderAddressForm address = new OrderAddressForm();
        address.setReceiverName("收货人");
        address.setReceiverPhone("13800000000");
        address.setAddress("验收地址");
        form.setAddress(address);
        SalesOrderItemForm item = new SalesOrderItemForm();
        item.setSkuId(skuId);
        item.setOrderedQuantity(quantity);
        item.setManualPriceOverride(false);
        form.setItems(List.of(item));
        return form;
    }

    private Long pricedSku(String type) {
        Long skuId = newSkuOfType("credit", type, "ON_SHELF");
        jdbc.update("UPDATE product_sku SET market_price=10 WHERE id=?", skuId);
        evictMybatisCache();
        return skuId;
    }

    @Test void actualWeightAmountControlsCreditAndFailureLeavesOrderPending() {
        Long customerId = newCustomer();
        Long skuId = pricedSku("NON_STANDARD");
        jdbc.update("UPDATE customer SET credit_limit=15 WHERE id=?", customerId);
        evictMybatisCache();
        var order = pending(customerId, skuId, "1.0000", "2.0000");
        var check = salesOrderService.creditCheck(order.getOrderId());
        assertThat(check.getRequestedOrderAmount()).isEqualByComparingTo("20.0000");
        assertThat(check.getAllowed()).isFalse();
        expectCode(() -> salesOrderService.confirm(salesOrderVersion(order), key()), 41201);
        assertThat(salesOrderQueryService.detail(order.getOrderId()).getStatus()).isEqualTo("PENDING");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void automaticStandardConfirmationCannotBypassCredit() {
        Long customerId = newCustomer();
        Long skuId = pricedSku("STANDARD");
        jdbc.update("UPDATE customer SET credit_limit=5 WHERE id=?", customerId);
        evictMybatisCache();
        expectCode(() -> salesOrderService.createAndProgress(orderForm(customerId, skuId, "1.0000"), key()), 41201);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sales_order WHERE customer_id=?", Integer.class, customerId)).isZero();
    }

    @Test void frozenSettlementSurvivesCustomerMasterChange() {
        Long customerId = newCustomer();
        Long otherId = newCustomer();
        Long skuId = pricedSku("NON_STANDARD");
        var order = pending(customerId, skuId, "1.0000", "1.0000");
        jdbc.update("UPDATE customer SET settlement_customer_id=? WHERE id=?", otherId, customerId);
        jdbc.update("UPDATE customer SET credit_limit=5 WHERE id=?", customerId);
        evictMybatisCache();
        expectCode(() -> salesOrderService.confirm(salesOrderVersion(order), key()), 41201);
        assertThat(jdbc.queryForObject("SELECT settlement_customer_id FROM sales_order WHERE id=?", Long.class, order.getOrderId()))
                .isEqualTo(customerId);
    }

    @Test void explicitCreditOverrideChecksPermissionAndFreezesReason() {
        Long customerId = newCustomer();
        Long skuId = pricedSku("NON_STANDARD");
        var order = pending(customerId, skuId, "1.0000", "1.0000");
        jdbc.update("UPDATE customer SET credit_limit=5 WHERE id=?", customerId);
        evictMybatisCache();
        var form = new com.xsy.scm.order.domain.form.OrderConfirmForm();
        form.setOrderId(order.getOrderId());
        form.setVersion(order.getVersion());
        form.setCreditOverride(true);
        form.setCreditOverrideReason("主管批准临时额度");
        try (var permissions = org.mockito.Mockito.mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
            salesOrderService.confirm(form,key());
            permissions.verify(() -> cn.dev33.satoken.stp.StpUtil.checkPermission(
                    com.xsy.scm.order.permission.OrderPermission.CREDIT_OVERRIDE));
        }
        assertThat(jdbc.queryForObject("SELECT credit_override_reason FROM sales_order WHERE id=?",String.class,order.getOrderId()))
                .isEqualTo("主管批准临时额度");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_operation_log WHERE order_id=? AND operation_type='CREDIT_OVERRIDE'",
                Integer.class,order.getOrderId())).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentConfirmationsShareOneCreditAccountLock() throws Exception {
        Long customerId = newCustomer();
        Long skuId = pricedSku("NON_STANDARD");
        var first = pending(customerId, skuId, "1.0000", "1.0000");
        var second = pending(customerId, skuId, "1.0000", "1.0000");
        jdbc.update("UPDATE customer SET credit_limit=15 WHERE id=?", customerId);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = List.of(first, second).stream().map(order -> executor.submit(() -> {
                RequestEmployee employee = new RequestEmployee();
                employee.setEmployeeId(1L);
                employee.setAdministratorFlag(true);
                employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                employee.setActualName("并发授信验收");
                SmartRequestUtil.setRequestUser(employee);
                try {
                    start.await(10, TimeUnit.SECONDS);
                    salesOrderService.confirm(salesOrderVersion(order), key());
                    return true;
                } catch (ScmBusinessException e) {
                    assertThat(e.getErrorCode().getCode()).isEqualTo(41201);
                    return false;
                } finally { SmartRequestUtil.remove(); }
            })).toList();
            start.countDown();
            int succeeded = 0;
            for (var result : results) if (result.get(20, TimeUnit.SECONDS)) succeeded++;
            assertThat(succeeded).isEqualTo(1);
        }
        assertThat(orderCreditService.check(customerId, BigDecimal.ZERO).getConfirmedOrderAmount())
                .isEqualByComparingTo("10.0000");
    }
}
