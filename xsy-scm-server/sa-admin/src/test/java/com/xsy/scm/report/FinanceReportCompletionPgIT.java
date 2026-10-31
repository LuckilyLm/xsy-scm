package com.xsy.scm.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.ScmW6PgITBase;
import com.xsy.scm.common.permission.ScmCrossDomainPermission;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.finance.domain.form.FinanceCustomerExpenseForm;
import com.xsy.scm.finance.service.FinanceCustomerExpenseService;
import com.xsy.scm.inventory.domain.InventoryOutboundFact;
import com.xsy.scm.inventory.service.InventoryFulfillmentService;
import com.xsy.scm.order.domain.form.OrderReturnIndependentAddForm;
import com.xsy.scm.order.domain.form.OrderReturnIndependentApproveForm;
import com.xsy.scm.order.domain.form.OrderReturnIndependentItemForm;
import com.xsy.scm.order.domain.form.OrderReturnReceiveForm;
import com.xsy.scm.order.domain.form.OrderReturnReceiveItemForm;
import com.xsy.scm.order.service.OrderReturnReceiptService;
import com.xsy.scm.order.service.OrderReturnService;
import com.xsy.scm.purchase.domain.form.PurchaseCostAdjustmentForm;
import com.xsy.scm.purchase.domain.form.PurchaseFinanceConfirmForm;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderVO;
import com.xsy.scm.purchase.domain.vo.PurchaseReceiptVO;
import com.xsy.scm.purchase.service.PurchaseCostAdjustmentService;
import com.xsy.scm.purchase.service.PurchaseFinanceConfirmService;
import com.xsy.scm.report.constant.ScmFinanceProfitDimensionEnum;
import com.xsy.scm.report.dao.ScmFinanceProfitDao;
import com.xsy.scm.report.domain.form.ScmCustomerMonthlyProfitQueryForm;
import com.xsy.scm.report.domain.form.ScmFinanceProfitQueryForm;
import com.xsy.scm.report.domain.form.ScmNegativeStockSnapshotForm;
import com.xsy.scm.report.service.ScmCustomerMonthlyProfitService;
import com.xsy.scm.report.service.ScmFinanceProfitService;
import com.xsy.scm.report.service.ScmNegativeStockSnapshotService;
import com.xsy.scm.report.support.ScmReportTimeRangeResolver;
import com.xsy.scm.warehouse.domain.form.WarehouseAddForm;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;

class FinanceReportCompletionPgIT extends ScmW6PgITBase {
    private static final AtomicLong SOURCE_IDS = new AtomicLong(System.currentTimeMillis());
    @Autowired private FinanceCustomerExpenseService expenseService;
    @Autowired private ScmCustomerMonthlyProfitService monthlyProfitService;
    @Autowired private PurchaseFinanceConfirmService confirmationService;
    @Autowired private PurchaseCostAdjustmentService costAdjustmentService;
    @Autowired private ScmFinanceProfitService profitService;
    @Autowired private ScmFinanceProfitDao profitDao;
    @Autowired private ScmNegativeStockSnapshotService snapshotService;
    @Autowired private OrderReturnService returnService;
    @Autowired private OrderReturnReceiptService returnReceiptService;
    @Autowired private InventoryFulfillmentService fulfillmentService;
    private MockedStatic<StpUtil> permissions;
    private Long warehouseId;

    @BeforeEach
    void permitFixtureCommands() {
        permissions = mockStatic(StpUtil.class);
        permissions.when(() -> StpUtil.hasPermission(anyString())).thenReturn(true);
        WarehouseAddForm warehouse = new WarehouseAddForm();
        warehouse.setName(prefix + "财务核对仓");
        warehouse.setAllowNegativeStock(true);
        warehouseId = warehouseService.create(warehouse);
    }

    @AfterEach
    void closePermissions() {
        permissions.close();
    }

    @Override
    protected Long fixtureWarehouseId() {
        return warehouseId;
    }

    @Test
    void expenseOnlyCustomerIsIncludedAndVoidingKeepsOriginalFact() {
        Long customerId = newCustomer();
        FinanceCustomerExpenseForm.Add form = new FinanceCustomerExpenseForm.Add();
        form.setCustomerId(customerId);
        form.setExpenseMonth(month());
        form.setTaxAmount("10.0000");
        form.setServiceAmount("20.0000");
        form.setFreightAmount("3.0000");
        form.setCommissionAmount("2.0000");
        form.setReason("按客户实际月费用登记");
        var expense = expenseService.add(form, prefix + ":expense");
        assertThat(expenseService.add(form, prefix + ":expense").getExpenseId()).isEqualTo(expense.getExpenseId());

        var query = monthlyQuery(customerId);
        var rows = monthlyProfitService.query(query).getList();
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getExpenseAmount()).isEqualByComparingTo("35.0000");
        assertThat(rows.getFirst().getNetProfit()).isEqualByComparingTo("-35.0000");
        assertThat(monthlyProfitService.summary(query).getNetProfit()).isEqualByComparingTo("-35.0000");
        assertThat(monthlyProfitService.export(query)).hasSize(1);

        FinanceCustomerExpenseForm.Void voidForm = new FinanceCustomerExpenseForm.Void();
        voidForm.setExpenseId(expense.getExpenseId());
        voidForm.setReason("费用所属月份登记错误");
        expenseService.voidExpense(voidForm, prefix + ":void");
        expenseService.voidExpense(voidForm, prefix + ":void");
        assertThat(monthlyProfitService.summary(query).getExpenseAmount()).isEqualByComparingTo("0.0000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_finance_customer_expense WHERE id = ?",
            Integer.class, expense.getExpenseId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_finance_customer_expense_void WHERE expense_id = ?",
            Integer.class, expense.getExpenseId())).isEqualTo(1);
        expectSqlFailure("UPDATE xsy_finance_customer_expense SET tax_amount = 0 WHERE id = ?", expense.getExpenseId());
        expectSqlFailure("DELETE FROM xsy_finance_customer_expense_void WHERE expense_id = ?", expense.getExpenseId());
    }

    @Test
    void monthlyActualCostDoesNotDependOnStockAndDistinguishesMissingFromZero() {
        Long customer = newCustomer();
        Long sku = newOnShelfSku("MONTH-COST");
        Long order = confirmedSalesOrder(customer, sku, "1.0000", "1000.0000");
        // 用不可变应收事实指定月内确认收入；不构造任何库存成本。
        jdbc.update("INSERT INTO xsy_finance_receivable(receivable_no, source_type, source_id, order_id, customer_id,"
            + " customer_name_snapshot, settlement_customer_id, settlement_customer_name_snapshot, entry_type, amount, event_at)"
            + " VALUES (?, 'SALES_ORDER', ?, ?, ?, '客户', ?, '客户', 'NORMAL', 1000, ?)",
            prefix + "-manual-month", order, order, customer, customer, OffsetDateTime.now());
        evictMybatisCache();
        var query = monthlyQuery(customer);
        assertThat(monthlyProfitService.summary(query).getRevenueAmount()).isEqualByComparingTo("1000");
        assertThat(monthlyProfitService.summary(query).getNetProfit()).isNull();
        assertThat(monthlyProfitService.summary(query).getSalesCostAmount()).isNull();
        var form = new FinanceCustomerExpenseForm.Add();
        form.setCustomerId(customer);
        form.setExpenseMonth(month());
        form.setGoodsCostAmount("100.0000");
        form.setTaxAmount("10.0000");
        form.setServiceAmount("0.0000");
        form.setFreightAmount("0.0000");
        form.setCommissionAmount("0.0000");
        form.setReason("按客户实际成本登记");
        var first = expenseService.add(form, prefix + ":goods");
        assertThat(monthlyProfitService.summary(query).getNetProfit()).isEqualByComparingTo("890");
        var range = ScmReportTimeRangeResolver.resolve(query);
        assertThat(profitDao.monthlySummary(range.startAt(), range.endAt(), query,
            scope(ScmValueScope.none(), ScmValueScope.all(), ScmValueScope.all())).getNetProfit()).isEqualByComparingTo("890");
        assertThat(profitDao.monthlySummary(range.startAt(), range.endAt(), query,
            scope(ScmValueScope.all(), ScmValueScope.none(), ScmValueScope.all())).getRevenueAmount()).isEqualByComparingTo("0");
        var correction = new FinanceCustomerExpenseForm.Void();
        correction.setExpenseId(first.getExpenseId());
        correction.setReason("原成本登记错误");
        expenseService.voidExpense(correction, prefix + ":goods-void");
        assertThat(monthlyProfitService.summary(query).getNetProfit()).isNull();
        form.setGoodsCostAmount("0.0000");
        form.setTaxAmount("0.0000");
        form.setReason("核实为零成本");
        expenseService.add(form, prefix + ":zero-cost");
        assertThat(monthlyProfitService.summary(query).getCostMissingCount()).isZero();
        assertThat(monthlyProfitService.summary(query).getNetProfit()).isEqualByComparingTo("1000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_inventory_movement WHERE sku_id = ?", Integer.class, sku)).isZero();
    }

    @Test
    void oneConfirmationPostsPayableWithoutStockAndPriceCorrectionsOnlyAppend() {
        Long skuId = newOnShelfSku("FIN-CONFIRM");
        PurchaseOrderVO order = submittedPurchase(skuId, "5.0000");
        var form = confirmationForm(order);
        var receipt = confirmationService.confirm(form, prefix + ":confirm");
        assertThat(confirmationService.confirm(form, prefix + ":confirm").getId()).isEqualTo(receipt.getId());
        assertThat(balanceRow(warehouseId, skuId)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_purchase_receipt WHERE purchase_order_id = ?",
            Integer.class, order.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_inventory_movement WHERE sku_id = ?",
            Integer.class, skuId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_purchase_account WHERE purchase_order_id = ?",
            Integer.class, order.getId())).isEqualTo(1);
        assertThatThrownBy(() -> confirmationService.confirm(form, prefix + ":duplicate")).isInstanceOf(com.xsy.scm.common.exception.ScmBusinessException.class);
        assertThatThrownBy(() -> submittedOrderReceipt(order.getId())).isInstanceOf(com.xsy.scm.common.exception.ScmBusinessException.class);
        assertThat(payableNet(order.getId())).isEqualByComparingTo("50.0000");
        assertThat(purchaseQueryService.orderDetail(order.getId()).getStatus()).isEqualTo("ACCOUNTED");

        var source = costAdjustmentService.options(order.getId()).getFirst();
        PurchaseCostAdjustmentForm adjustment = new PurchaseCostAdjustmentForm();
        adjustment.setAccountItemId(source.getAccountItemId());
        adjustment.setExpectedPrice("5.0000");
        adjustment.setNewPrice("6.0000");
        adjustment.setReason("供应商对账调增");
        var increased = costAdjustmentService.add(adjustment, prefix + ":increase");
        assertThat(payableNet(order.getId())).isEqualByComparingTo("60.0000");
        assertThat(costAdjustmentService.add(adjustment, prefix + ":increase").getId()).isEqualTo(increased.getId());

        adjustment.setPreviousAdjustmentId(increased.getId());
        adjustment.setExpectedPrice("6.0000");
        adjustment.setNewPrice("0.0000");
        adjustment.setReason("整笔采购价款核减");
        costAdjustmentService.add(adjustment, prefix + ":decrease");
        assertThat(payableNet(order.getId())).isEqualByComparingTo("0.0000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_finance_purchase_cost_entry WHERE original_payable_id IS NOT NULL"
            + " AND adjustment_id = (SELECT max(id) FROM xsy_purchase_cost_adjustment WHERE account_item_id = ?)",
            Integer.class, source.getAccountItemId())).isEqualTo(2);
        assertThat(balanceRow(warehouseId, skuId)).isNull();
        assertThat(jdbc.queryForObject("SELECT unit_price FROM xsy_purchase_account_item WHERE id = ?",
            BigDecimal.class, source.getAccountItemId())).isEqualByComparingTo("5.0000");
        assertThat(jdbc.queryForObject("SELECT purchase_price FROM xsy_purchase_order_item WHERE id = ?",
            BigDecimal.class, source.getPurchaseOrderItemId())).isEqualByComparingTo("5.0000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_inventory_movement WHERE sku_id = ?",
            Integer.class, skuId)).isZero();
        expectSqlFailure("UPDATE xsy_purchase_account_item SET unit_price = 1 WHERE id = ?", source.getAccountItemId());

        var profit = profitQuery();
        profit.setSkuId(skuId);
        assertThat(profitService.summary(profit).getSalesCostAmount()).isEqualByComparingTo("-50.0000");
        expectSqlFailure("UPDATE xsy_purchase_cost_adjustment SET amount = 1 WHERE id = ?", increased.getId());
        expectSqlFailure("DELETE FROM xsy_finance_purchase_cost_entry WHERE adjustment_id = ?", increased.getId());
    }

    @Test
    void independentReturnRevenueAndReceivedCostAppearWithIntersectedScope() {
        Long customerId = newCustomer();
        Long skuId = newOnShelfSku("FIN-RETURN");
        jdbc.update("UPDATE xsy_customer SET seller_id = 1 WHERE id = ?", customerId);
        jdbc.update("INSERT INTO xsy_inventory_balance(warehouse_id, sku_id, unit, quantity, avg_cost)"
            + " VALUES (?, ?, 'kg', 10, 5)", warehouseId, skuId);
        evictMybatisCache();
        OrderReturnIndependentItemForm item = new OrderReturnIndependentItemForm();
        item.setSkuId(skuId);
        item.setRequestedQuantity("2.0000");
        item.setUnitPrice("8.0000");
        item.setPriceReason("核实客户退货单价");
        OrderReturnIndependentAddForm add = new OrderReturnIndependentAddForm();
        add.setCustomerId(customerId);
        add.setReason("独立退货");
        add.setItems(List.of(item));
        var created = returnService.createIndependent(add, prefix + ":return");
        OrderReturnIndependentApproveForm approve = new OrderReturnIndependentApproveForm();
        approve.setReturnId(created.getReturnId());
        approve.setVersion(created.getVersion());
        OrderReturnIndependentApproveForm.Item approvedLine = new OrderReturnIndependentApproveForm.Item();
        approvedLine.setReturnItemId(created.getItems().getFirst().getReturnItemId());
        approvedLine.setApprovedQuantity("2.0000");
        approve.setItems(List.of(approvedLine));
        var approved = returnService.approveIndependent(approve, prefix + ":approve");

        var query = profitQuery();
        query.setDimension(ScmFinanceProfitDimensionEnum.CUSTOMER);
        query.setCustomerId(customerId);
        assertThat(profitService.summary(query).getRevenueAmount()).isEqualByComparingTo("-16.0000");
        var range = ScmReportTimeRangeResolver.resolve(query);
        var scope = scope(ScmValueScope.of(List.of(warehouseId)), ScmValueScope.of(List.of(1L)), ScmValueScope.of(List.of(1L)));
        assertThat(profitDao.summary(range.startAt(), range.endAt(), query, scope).getRevenueAmount()).isEqualByComparingTo("0.0000");

        OrderReturnReceiveForm receive = new OrderReturnReceiveForm();
        receive.setReturnId(approved.getReturnId());
        receive.setVersion(approved.getVersion());
        receive.setWarehouseId(warehouseId);
        OrderReturnReceiveItemForm receivedLine = new OrderReturnReceiveItemForm();
        receivedLine.setReturnItemId(approvedLine.getReturnItemId());
        receivedLine.setQuantity("2.0000");
        receivedLine.setDisposition("RETURN_TO_STOCK");
        receive.setItems(List.of(receivedLine));
        returnReceiptService.receive(receive, prefix + ":receive");
        var summary = profitDao.summary(range.startAt(), range.endAt(), query, scope);
        assertThat(summary.getRevenueAmount()).isEqualByComparingTo("-16.0000");
        assertThat(summary.getSalesCostAmount()).isEqualByComparingTo("-10.0000");
        assertThat(summary.getGrossProfit()).isEqualByComparingTo("-6.0000");
        var denied = scope(ScmValueScope.all(), ScmValueScope.of(List.of(1L)), ScmValueScope.of(List.of(2L)));
        assertThat(profitDao.query(new Page<>(1, 20), range.startAt(), range.endAt(), query, denied)).isEmpty();
    }

    @Test
    void unpricedZeroCostRemainsIncompleteAfterLaterPurchaseButKnownFreeCostIsValid() {
        Long unknownSku = newOnShelfSku("UNKNOWN-COST");
        dispatch(unknownSku);
        var query = profitQuery();
        query.setSkuId(unknownSku);
        assertThat(profitService.summary(query).getCostMissingCount()).isEqualTo(1);
        assertThat(profitService.summary(query).getGrossProfit()).isNull();
        confirmPurchase(unknownSku, "5.0000");
        assertThat(profitService.summary(query).getCostMissingCount()).isEqualTo(1);

        Long freeSku = newOnShelfSku("KNOWN-FREE");
        confirmPurchase(freeSku, "0.0000");
        dispatch(freeSku);
        query.setSkuId(freeSku);
        assertThat(profitService.summary(query).getCostMissingCount()).isZero();
        assertThat(profitService.summary(query).getGrossProfit()).isEqualByComparingTo("0.0000");
    }

    @Test
    void monthlySnapshotIsImmutableAndKeepsOriginalRowsAfterStockChanges() {
        Long skuId = newOnShelfSku("SNAPSHOT");
        inventoryCommandService.postSalesOutbound(new InventoryOutboundFact(warehouseId, skuId, 0L,
            SOURCE_IDS.incrementAndGet(), new BigDecimal("2.0000"), null, OffsetDateTime.now(), "test:1", "kg"));
        ScmNegativeStockSnapshotForm.Create form = new ScmNegativeStockSnapshotForm.Create();
        form.setWarehouseId(warehouseId);
        form.setRemark("月末核对");
        var snapshot = snapshotService.create(form, prefix + ":snapshot");
        assertThat(snapshotService.create(form, prefix + ":snapshot").getId()).isEqualTo(snapshot.getId());
        assertThat(snapshot.getRows().get(0).path("quantity").asText()).isEqualTo("-2.0000");
        assertThat(snapshot.getZeroCostRowCount()).isEqualTo(1);
        confirmPurchase(skuId, "5.0000");
        assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("8.0000");
        assertThat(snapshotService.detail(snapshot.getId()).getRows().get(0).path("quantity").asText()).isEqualTo("-2.0000");
        expectSqlFailure("DELETE FROM xsy_report_negative_stock_snapshot WHERE id = ?", snapshot.getId());
    }

    @Test
    void monthlyProfitAndSnapshotRequireCostPermissionEvenForUnrestrictedDataScope() {
        permissions.when(() -> StpUtil.hasPermission(ScmCrossDomainPermission.REPORT_COST_QUERY)).thenReturn(false);
        assertThatThrownBy(() -> monthlyProfitService.query(monthlyQuery(newCustomer()))).isInstanceOf(ScmDataScopeException.class);
        ScmNegativeStockSnapshotForm.Create form = new ScmNegativeStockSnapshotForm.Create();
        form.setWarehouseId(warehouseId);
        assertThatThrownBy(() -> snapshotService.create(form, prefix + ":no-cost")).isInstanceOf(ScmDataScopeException.class);
    }

    private PurchaseOrderVO submittedPurchase(Long skuId, String price) {
        Long supplierId = newPurchasableSupplier("FIN" + SOURCE_IDS.incrementAndGet(), skuId);
        var order = createDraftOrder("P" + SOURCE_IDS.incrementAndGet(), supplierId, skuId, "10.0000", price);
        submitOrder(order.getId());
        return purchaseQueryService.orderDetail(order.getId());
    }

    private PurchaseReceiptVO confirmPurchase(Long skuId, String price) {
        // 库存成本历史用例仍显式走原收货链；采购确认不再隐式补库存。
        var order = submittedPurchase(skuId, price);
        var receipt = submittedOrderReceipt(order.getId());
        return confirmReceipt(receipt.getId(), "10.0000");
    }

    private PurchaseFinanceConfirmForm confirmationForm(PurchaseOrderVO order) {
        var line = order.getItems().getFirst();
        PurchaseFinanceConfirmForm form = new PurchaseFinanceConfirmForm();
        form.setPurchaseOrderId(order.getId());
        form.setVersion(order.getVersion());
        PurchaseFinanceConfirmForm.Item item = new PurchaseFinanceConfirmForm.Item();
        item.setOrderItemId(line.getId());
        item.setVersion(line.getVersion());
        item.setPurchaseUnit(line.getPurchaseUnit());
        item.setExpectedPrice(line.getPurchasePrice().toPlainString());
        item.setQuantity("10.0000");
        form.setItems(List.of(item));
        return form;
    }

    private void dispatch(Long skuId) {
        Long orderId = confirmedSalesOrder(newCustomer(), skuId, "1.0000", "1.0000");
        Long itemId = confirmedSalesOrderItemId(orderId);
        fulfillmentService.dispatchOutbound(new InventoryFulfillmentService.Command(SOURCE_IDS.incrementAndGet(), warehouseId,
            OffsetDateTime.now(), "test:1", List.of(new InventoryFulfillmentService.Line(orderId, itemId, skuId,
                BigDecimal.ONE.setScale(4), "kg", BigDecimal.ONE))));
    }

    private BigDecimal payableNet(Long orderId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN entry_type = 'NORMAL' THEN amount ELSE -amount END), 0)"
            + " FROM xsy_finance_payable WHERE purchase_order_id = ? AND deleted = FALSE", BigDecimal.class, orderId);
    }

    private LocalDate month() {
        return LocalDate.now(ZoneId.of("Asia/Shanghai")).withDayOfMonth(1);
    }

    private ScmFinanceProfitQueryForm profitQuery() {
        ScmFinanceProfitQueryForm form = new ScmFinanceProfitQueryForm();
        form.setStartDate(month());
        form.setEndDate(month().plusMonths(1).minusDays(1));
        form.setDimension(ScmFinanceProfitDimensionEnum.PRODUCT);
        form.setPageNum(1L);
        form.setPageSize(20L);
        return form;
    }

    private ScmCustomerMonthlyProfitQueryForm monthlyQuery(Long customerId) {
        ScmCustomerMonthlyProfitQueryForm form = new ScmCustomerMonthlyProfitQueryForm();
        form.setStartDate(month());
        form.setEndDate(month().plusMonths(1).minusDays(1));
        form.setDimension(ScmFinanceProfitDimensionEnum.CUSTOMER);
        form.setCustomerId(customerId);
        form.setPageNum(1L);
        form.setPageSize(20L);
        return form;
    }

    private ScmDataScopeContext scope(ScmValueScope warehouses, ScmValueScope orders, ScmValueScope customers) {
        var scope = mock(ScmDataScopeContext.class);
        when(scope.getWarehouseScope()).thenReturn(warehouses);
        when(scope.getOrderSellerScope()).thenReturn(orders);
        when(scope.getCustomerSellerScope()).thenReturn(customers);
        when(scope.getPurchaserScope()).thenReturn(ScmValueScope.all());
        when(scope.isCostVisible()).thenReturn(true);
        return scope;
    }
}

