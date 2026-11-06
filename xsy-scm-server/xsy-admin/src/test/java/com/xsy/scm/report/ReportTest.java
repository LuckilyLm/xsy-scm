package com.xsy.scm.report;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.XsyApplication;
import com.xsy.scm.admin.module.system.login.domain.RequestEmployee;
import com.xsy.scm.admin.test.PgITPaths;
import com.xsy.scm.base.common.domain.PageParam;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.enumeration.UserTypeEnum;
import com.xsy.scm.base.common.util.XsyRequestUtil;
import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.ScmW6PgITBase;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.permission.ScmCrossDomainPermission;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.finance.domain.form.FinanceCustomerExpenseForm;
import com.xsy.scm.finance.domain.form.FinancePostingAddForm;
import com.xsy.scm.finance.domain.form.FinancePostingVoidForm;
import com.xsy.scm.finance.domain.vo.FinanceCustomerExpenseVO;
import com.xsy.scm.finance.domain.vo.FinancePostingVO;
import com.xsy.scm.finance.permission.FinancePermission;
import com.xsy.scm.finance.service.FinanceCustomerExpenseService;
import com.xsy.scm.finance.service.FinancePostingService;
import com.xsy.scm.inventory.domain.InventoryOutboundFact;
import com.xsy.scm.inventory.service.InventoryFulfillmentService;
import com.xsy.scm.order.domain.form.OrderReturnIndependentAddForm;
import com.xsy.scm.order.domain.form.OrderReturnIndependentApproveForm;
import com.xsy.scm.order.domain.form.OrderReturnIndependentItemForm;
import com.xsy.scm.order.domain.form.OrderReturnReceiveForm;
import com.xsy.scm.order.domain.form.OrderReturnReceiveItemForm;
import com.xsy.scm.order.service.OrderReturnReceiptService;
import com.xsy.scm.order.service.OrderReturnService;
import com.xsy.scm.purchase.constant.ScmPurchaseStatusEnum;
import com.xsy.scm.purchase.constant.ScmReceiptModeEnum;
import com.xsy.scm.purchase.domain.form.PurchaseCostAdjustmentForm;
import com.xsy.scm.purchase.domain.form.PurchaseFinanceConfirmForm;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptCreateForm;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderVO;
import com.xsy.scm.purchase.domain.vo.PurchaseReceiptVO;
import com.xsy.scm.purchase.service.PurchaseCostAdjustmentService;
import com.xsy.scm.purchase.service.PurchaseFinanceConfirmService;
import com.xsy.scm.report.constant.ReportErrorCode;
import com.xsy.scm.report.constant.ScmFinanceProfitDimensionEnum;
import com.xsy.scm.report.constant.ScmOrderExceptionTypeEnum;
import com.xsy.scm.report.controller.PurchaseDailyReportController;
import com.xsy.scm.report.controller.ScmCustomerStatementController;
import com.xsy.scm.report.controller.ScmReportController;
import com.xsy.scm.report.controller.ScmSupplierStatementController;
import com.xsy.scm.report.dao.PurchaseDailyReportDao;
import com.xsy.scm.report.dao.ReportDao;
import com.xsy.scm.report.dao.ScmFinanceProfitDao;
import com.xsy.scm.report.dao.ScmFinanceReportDao;
import com.xsy.scm.report.dao.ScmOrderExceptionReadDao;
import com.xsy.scm.report.domain.form.PurchaseDailyQueryForm;
import com.xsy.scm.report.domain.form.ScmCustomerMonthlyProfitQueryForm;
import com.xsy.scm.report.domain.form.ScmCustomerStatementForm;
import com.xsy.scm.report.domain.form.ScmFinanceOverviewQueryForm;
import com.xsy.scm.report.domain.form.ScmFinanceProfitQueryForm;
import com.xsy.scm.report.domain.form.ScmFinanceReportQueryForm;
import com.xsy.scm.report.domain.form.ScmInventoryReportQueryForm;
import com.xsy.scm.report.domain.form.ScmNegativeStockSnapshotForm;
import com.xsy.scm.report.domain.form.ScmOrderExceptionQueryForm;
import com.xsy.scm.report.domain.form.ScmOverviewReportQueryForm;
import com.xsy.scm.report.domain.form.ScmPurchaseReportQueryForm;
import com.xsy.scm.report.domain.form.ScmReceiptReportQueryForm;
import com.xsy.scm.report.domain.form.ScmSalesReportQueryForm;
import com.xsy.scm.report.domain.vo.InventoryReportVO;
import com.xsy.scm.report.domain.vo.PurchaseDailyReportVO;
import com.xsy.scm.report.domain.vo.PurchaseReportVO;
import com.xsy.scm.report.domain.vo.ReceiptReportVO;
import com.xsy.scm.report.domain.vo.ReportDailyStatVO;
import com.xsy.scm.report.domain.vo.ReportOverviewVO;
import com.xsy.scm.report.domain.vo.SalesReportVO;
import com.xsy.scm.report.domain.vo.ScmCustomerMonthlyProfitVO;
import com.xsy.scm.report.domain.vo.ScmCustomerStatementItemVO;
import com.xsy.scm.report.domain.vo.ScmCustomerStatementVO;
import com.xsy.scm.report.domain.vo.ScmFinanceOverviewVO;
import com.xsy.scm.report.domain.vo.ScmFinancePayableDetailVO;
import com.xsy.scm.report.domain.vo.ScmFinanceReceivableDetailVO;
import com.xsy.scm.report.domain.vo.ScmSupplierStatementItemVO;
import com.xsy.scm.report.domain.vo.ScmSupplierStatementVO;
import com.xsy.scm.report.job.PurchaseDailyReportJob;
import com.xsy.scm.report.service.InventoryReportService;
import com.xsy.scm.report.service.OverviewReportService;
import com.xsy.scm.report.service.PurchaseDailyGenerationService;
import com.xsy.scm.report.service.PurchaseDailyQueryService;
import com.xsy.scm.report.service.PurchaseReportService;
import com.xsy.scm.report.service.ReceiptReportService;
import com.xsy.scm.report.service.SalesReportService;
import com.xsy.scm.report.service.ScmCustomerMonthlyExcelService;
import com.xsy.scm.report.service.ScmCustomerMonthlyProfitService;
import com.xsy.scm.report.service.ScmCustomerStatementService;
import com.xsy.scm.report.service.ScmFinanceProfitService;
import com.xsy.scm.report.service.ScmNegativeStockSnapshotService;
import com.xsy.scm.report.service.ScmSupplierStatementService;
import com.xsy.scm.report.support.PurchasePriceTrendComparison;
import com.xsy.scm.report.support.ScmReportAccess;
import com.xsy.scm.report.support.ScmReportTimeRange;
import com.xsy.scm.report.support.ScmReportTimeRangeResolver;
import com.xsy.scm.warehouse.domain.form.WarehouseAddForm;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReportTest {

    @Nested
    class FinanceReportCompletionPgIT extends ScmW6PgITBase {
        private static final AtomicLong SOURCE_IDS = new AtomicLong(System.currentTimeMillis());
        @Autowired private FinanceCustomerExpenseService expenseService;
        @Autowired private com.xsy.scm.report.dao.ReportDao purchaseReportDao;
        @Autowired private ScmCustomerMonthlyProfitService monthlyProfitService;
        @Autowired private PurchaseFinanceConfirmService confirmationService;
        @Autowired private PurchaseCostAdjustmentService costAdjustmentService;
        @Autowired private ScmFinanceProfitService profitService;
        @Autowired private ScmFinanceProfitDao profitDao;
        @Autowired private ScmNegativeStockSnapshotService snapshotService;
        @Autowired private OrderReturnService returnService;
        @Autowired private OrderReturnReceiptService returnReceiptService;
        @Autowired private InventoryFulfillmentService fulfillmentService;
        @Autowired private TransactionTemplate transactionTemplate;
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
            assertAppendOnlyDao(com.xsy.scm.finance.dao.FinanceCustomerExpenseDao.class);
            assertThat(jdbc.queryForObject("SELECT tax_amount FROM xsy_finance_customer_expense WHERE id=?",
                BigDecimal.class, expense.getExpenseId())).isEqualByComparingTo("10.0000");
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        void batchExpenseWritesAllCustomersAndRollsBackTheWholeBatchOnOneBadRow() {
            Long first = newCustomer();
            Long second = newCustomer();
            FinanceCustomerExpenseForm.BatchAdd form = new FinanceCustomerExpenseForm.BatchAdd();
            form.setExpenseMonth(month());
            form.setReason("批量登记十月费用");
            form.setRows(List.of(batchRow(first, "100.0000"), batchRow(second, "50.0000")));

            var created = expenseService.addBatch(form, prefix + ":batch-expense");
            assertThat(created).hasSize(2);
            assertThat(created).extracting(FinanceCustomerExpenseVO::getCustomerId).containsExactlyInAnyOrder(first, second);
            // 同键重试直接返回原批次，不重复登记。
            assertThat(expenseService.addBatch(form, prefix + ":batch-expense")).hasSize(2);
            assertThat(countExpense(first)).isEqualTo(1);

            Long untouched = newCustomer();
            FinanceCustomerExpenseForm.BatchAdd invalid = new FinanceCustomerExpenseForm.BatchAdd();
            invalid.setExpenseMonth(month());
            invalid.setReason("包含无效客户");
            invalid.setRows(List.of(batchRow(untouched, "1.0000"), batchRow(987_654_321L, "1.0000")));
            // 直接调用 Bean 不会经过 @Transactional 代理，这里用显式事务模拟控制器入口的整批回滚。
            assertThatThrownBy(() -> transactionTemplate.execute(status -> {
                expenseService.addBatch(invalid, prefix + ":batch-expense-bad");
                return null;
            })).isInstanceOf(ScmBusinessException.class);
            assertThat(countExpense(untouched)).isZero();
        }

        private FinanceCustomerExpenseForm.Row batchRow(Long customerId, String goodsCost) {
            FinanceCustomerExpenseForm.Row row = new FinanceCustomerExpenseForm.Row();
            row.setCustomerId(customerId);
            row.setGoodsCostAmount(goodsCost);
            row.setTaxAmount("10.0000");
            row.setServiceAmount("0.0000");
            row.setFreightAmount("0.0000");
            row.setCommissionAmount("0.0000");
            return row;
        }

        private int countExpense(Long customerId) {
            return jdbc.queryForObject("SELECT count(*) FROM xsy_finance_customer_expense WHERE customer_id = ?",
                Integer.class, customerId);
        }

        @Test
        void monthlyActualCostDoesNotDependOnStockAndDistinguishesMissingFromZero() {
            Long customer = newCustomer();
            Long sku = newOnShelfSku("MONTH-COST");
            Long order = confirmedSalesOrder(customer, sku, "1.0000", "1000.0000");
            // 用不可修改应收记录指定月内确认收入；不构造任何库存成本。
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
            assertAppendOnlyDao(com.xsy.scm.purchase.dao.PurchaseAccountDao.class);

            var profit = profitQuery();
            profit.setSkuId(skuId);
            assertThat(profitService.summary(profit).getSalesCostAmount()).isEqualByComparingTo("-50.0000");
            assertAppendOnlyDao(com.xsy.scm.purchase.dao.PurchaseCostAdjustmentDao.class);
            assertAppendOnlyDao(com.xsy.scm.finance.dao.FinancePurchaseCostEntryDao.class);
        }

        @Test
        void correctionOfPriorMonthPurchaseAppearsOnlyInCorrectionMonth() {
            Long sku = newOnShelfSku("PERIOD-PRICE");
            var receipt = confirmPurchase(sku, "5.0000");
            jdbc.update("UPDATE xsy_purchase_order SET submitted_at = ? WHERE id = ?",
                month().minusMonths(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toOffsetDateTime(), receipt.getPurchaseOrderId());
            evictMybatisCache();
            var source = costAdjustmentService.options(receipt.getPurchaseOrderId()).getFirst();
            var adjustment = new PurchaseCostAdjustmentForm();
            adjustment.setReceiptItemId(source.getReceiptItemId());
            adjustment.setExpectedPrice("5.0000"); adjustment.setNewPrice("6.0000"); adjustment.setReason("跨月对账更正");
            costAdjustmentService.add(adjustment, prefix + ":period-adjust");
            var query = new com.xsy.scm.report.domain.form.ScmPurchaseReportQueryForm();
            query.setSkuId(sku);
            var from = month().atStartOfDay(ZoneId.of("Asia/Shanghai")).toOffsetDateTime();
            var current = purchaseReportDao.purchaseByProduct(new Page<>(1, 20), from, from.plusMonths(1), query, ScmValueScope.all());
            assertThat(current).hasSize(1);
            assertThat(current.getFirst().getAdjustmentAmount()).isEqualByComparingTo("10");
            assertThat(current.getFirst().getOrderAmount()).isEqualByComparingTo("0");
            var previous = purchaseReportDao.purchaseByProduct(new Page<>(1, 20), from.minusMonths(1), from, query, ScmValueScope.all());
            assertThat(previous).hasSize(1);
            assertThat(previous.getFirst().getAdjustmentAmount()).isEqualByComparingTo("0");
        }

        @Test
        void confirmationUsesReviewedFeedbackPriceAndActualQuantity() {
            Long sku = newOnShelfSku("FEEDBACK-CONFIRM");
            var order = submittedPurchase(sku, "5.0000");
            Long itemId = order.getItems().getFirst().getId();
            Long feedbackId = jdbc.queryForObject("INSERT INTO xsy_purchase_share_feedback(share_token_id,purchase_order_id,"
                + "purchase_order_item_id,unit_price,received_quantity,created_by) VALUES (1,?,?,6,8,'it') RETURNING id",
                Long.class, order.getId(), itemId);
            evictMybatisCache();
            assertThat(confirmationService.feedback(order.getId())).singleElement().satisfies(row -> {
                assertThat(row.getId()).isEqualTo(feedbackId);
                assertThat(row.getReceivedQuantity()).isEqualByComparingTo("8");
            });
            var form = confirmationForm(order);
            form.getItems().getFirst().setQuantity("8.0000");
            form.getItems().getFirst().setFeedbackId(feedbackId);
            form.getItems().getFirst().setConfirmedPrice("6.0000");
            var account = confirmationService.confirm(form, prefix + ":feedback-confirm");
            assertThat(account.getItems()).singleElement().satisfies(row -> {
                assertThat(row.getQuantity()).isEqualByComparingTo("8");
                assertThat(row.getUnitPrice()).isEqualByComparingTo("6");
                assertThat(row.getAmount()).isEqualByComparingTo("48");
            });
            assertThat(payableNet(order.getId())).isEqualByComparingTo("48");
            assertThat(confirmationService.confirm(form, prefix + ":feedback-confirm").getId()).isEqualTo(account.getId());
            assertThat(balanceRow(warehouseId, sku)).isNull();
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
            assertAppendOnlyDao(com.xsy.scm.report.dao.ScmNegativeStockSnapshotDao.class);
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

        private void assertAppendOnlyDao(Class<?> dao) {
            assertThat(java.util.Arrays.stream(dao.getMethods()).map(java.lang.reflect.Method::getName))
                .noneMatch(name -> name.matches("(?i)(update|delete|remove).*"));
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

    @Nested
    class ReportUnitTest {
        @Nested

        class PurchaseDailyReportJobTest {
            private final PurchaseDailyGenerationService generationService = mock(PurchaseDailyGenerationService.class);
            private final PurchaseDailyReportJob job = new PurchaseDailyReportJob(generationService);

            @Test
            void emptyParameterTargetsPreviousShanghaiDay() {
                LocalDate yesterday = LocalDate.now(ScmReportTimeRangeResolver.BUSINESS_ZONE).minusDays(1);
                when(generationService.generate(yesterday)).thenReturn(true);

                assertThat(job.run(null)).contains(yesterday.toString(), "已生成");
                verify(generationService).generate(yesterday);
            }

            @Test
            void manualDateDoesNotOverwriteExistingSnapshot() {
                LocalDate date = LocalDate.of(2020, 1, 2);
                when(generationService.generate(date)).thenReturn(false);

                assertThat(job.run(" 2020-01-02 ")).contains("已存在", "保留原始数据");
                verify(generationService).generate(date);
            }

            @ParameterizedTest
            @ValueSource(strings = {"2020-02-30", "2020/01/02", "yesterday", "2020-1-2"})
            void malformedBackfillDateFailsInsteadOfSilentlyGeneratingYesterday(String parameter) {
                assertThatThrownBy(() -> job.run(parameter)).isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("yyyy-MM-dd");
                verifyNoInteractions(generationService);
            }
        }

        @Nested

        class PurchasePriceTrendComparisonTest {
            @Test
            void comparesPreviousValidDayOnlyWithinSameSkuAndPurchaseUnit() {
                var first = point("2026-10-01", 1L, "kg", "10.0000");
                var second = point("2026-10-03", 1L, "kg", "12.5000");
                var otherUnit = point("2026-10-04", 1L, "箱", "120.0000");
                var otherSku = point("2026-10-05", 2L, "kg", "20.0000");
                var third = point("2026-10-05", 1L, "kg", "10.0000");

                PurchasePriceTrendComparison.populate(new ArrayList<>(List.of(third, second, otherSku, first, otherUnit)));

                assertThat(first.getPreviousWeightedAvgPrice()).isNull();
                assertThat(second.getPreviousWeightedAvgPrice()).isEqualByComparingTo("10.0000");
                assertThat(second.getPriceChangeAmount()).isEqualByComparingTo("2.5000");
                assertThat(second.getPriceChangePercent()).isEqualByComparingTo("25.0000");
                assertThat(third.getPreviousWeightedAvgPrice()).isEqualByComparingTo("12.5000");
                assertThat(third.getPriceChangeAmount()).isEqualByComparingTo("-2.5000");
                assertThat(third.getPriceChangePercent()).isEqualByComparingTo("-20.0000");
                assertThat(otherUnit.getPriceChangePercent()).isNull();
                assertThat(otherSku.getPriceChangeAmount()).isNull();
            }

            @Test
            void zeroBasisKeepsAmountButPercentIsUnknown() {
                var zero = point("2026-10-01", 1L, "kg", "0.0000");
                var later = point("2026-10-02", 1L, "kg", "6.0000");

                PurchasePriceTrendComparison.populate(List.of(zero, later));

                assertThat(later.getPreviousWeightedAvgPrice()).isEqualByComparingTo("0");
                assertThat(later.getPriceChangeAmount()).isEqualByComparingTo("6.0000");
                assertThat(later.getPriceChangePercent()).isNull();
            }

            @Test
            void missingSampleDoesNotBecomeZeroOrBreakPriorValidObservation() {
                var first = point("2026-10-01", 1L, "kg", "8.0000");
                var missing = point("2026-10-02", 1L, "kg", null);
                var next = point("2026-10-03", 1L, "kg", "9.0000");

                PurchasePriceTrendComparison.populate(List.of(first, missing, next));

                assertThat(missing.getPriceChangeAmount()).isNull();
                assertThat(missing.getPreviousWeightedAvgPrice()).isNull();
                assertThat(next.getPreviousWeightedAvgPrice()).isEqualByComparingTo("8.0000");
                assertThat(next.getPriceChangePercent()).isEqualByComparingTo("12.5000");
            }

            @Test
            void reapplyingComparisonClearsStaleValuesAndEmptyInputIsAllowed() {
                assertThat(PurchasePriceTrendComparison.populate(List.of())).isEmpty();
                assertThat(PurchasePriceTrendComparison.populate(null)).isEmpty();
                var single = point("2026-10-01", 1L, "kg", "3.0000");
                single.setPriceChangeAmount(new BigDecimal("999.0000"));
                single.setPriceChangePercent(new BigDecimal("99.0000"));

                PurchasePriceTrendComparison.populate(List.of(single));

                assertThat(single.getPriceChangeAmount()).isNull();
                assertThat(single.getPriceChangePercent()).isNull();
            }

            private static PurchaseReportVO.PriceTrendPoint point(String date, Long skuId, String unit, String price) {
                var point = new PurchaseReportVO.PriceTrendPoint();
                point.setBizDate(date);
                point.setSkuId(skuId);
                point.setPurchaseUnit(unit);
                if (price != null) {
                    point.setWeightedAvgPrice(new BigDecimal(price));
                }
                return point;
            }
        }

        @Nested

        class ScmCustomerMonthlyExcelServiceTest {
            @Test
            void preservesCustomerHeadersAndReconcilesEveryDerivedAmount() throws Exception {
                var report = mock(ScmCustomerMonthlyProfitService.class);
                var query = new ScmCustomerMonthlyProfitQueryForm();
                query.setStartDate(LocalDate.of(2026, 11, 1));
                query.setEndDate(LocalDate.of(2026, 11, 30));
                var row = new ScmCustomerMonthlyProfitVO();
                row.setDimensionName("测试客户");
                row.setStatementAmount(new BigDecimal("1050"));
                row.setPaidAmount(new BigDecimal("300"));
                row.setPostingAmount(new BigDecimal("50"));
                row.setPostingTaxAmount(new BigDecimal("6.5"));
                row.setSalesCostAmount(new BigDecimal("600"));
                row.setFreightAmount(new BigDecimal("5"));
                row.setCommissionAmount(new BigDecimal("3"));
                when(report.export(query)).thenReturn(List.of(row));
                var service = new ScmCustomerMonthlyExcelService(report);
                try (var book = WorkbookFactory.create(new ByteArrayInputStream(service.export(query)))) {
                    var sheet = book.getSheetAt(0);
                    assertThat(sheet.getRow(2).getCell(2).getStringCellValue()).isEqualTo("对账日期");
                    assertThat(sheet.getRow(3).getCell(2).getStringCellValue()).isEqualTo("2026-11-01 至 2026-11-30");
                    assertThat(sheet.getRow(3).getCell(3).getCellType()).isEqualTo(CellType.NUMERIC);
                    assertThat(sheet.getRow(3).getCell(5).getNumericCellValue()).isEqualTo(750);
                    assertThat(sheet.getRow(3).getCell(8).getNumericCellValue()).isEqualTo(993.5);
                    assertThat(sheet.getRow(3).getCell(12).getNumericCellValue()).isEqualTo(368.5);
                    assertThat(sheet.getRow(4).getCell(12).getNumericCellValue()).isEqualTo(368.5);
                    assertThat(sheet.isColumnHidden(13)).isTrue();
                }
                row.setSalesCostAmount(null);
                try (var book = WorkbookFactory.create(new ByteArrayInputStream(service.export(query)))) {
                    var sheet = book.getSheetAt(0);
                    assertThat(sheet.getRow(3).getCell(9).getCellType()).isEqualTo(CellType.BLANK);
                    assertThat(sheet.getRow(3).getCell(12).getStringCellValue()).isEmpty();
                    assertThat(sheet.getRow(4).getCell(12).getCellType()).isEqualTo(CellType.BLANK);
                }
            }
        }

        @Nested

        class ScmStatementExportLabelsTest {
            @Test
            void customerWorkbookUsesPlainLabelsAndPreservesStoredAmounts() throws Exception {
                var service = mock(ScmCustomerStatementService.class);
                var version = new ScmCustomerStatementVO();
                version.setId(1L);
                version.setPartialScope(false);
                version.setItems(List.of("RECEIVABLE", "RED", "RECEIPT", "RECEIPT_REVERSE", "WRITE_OFF",
                    "WRITE_OFF_REVERSE", "WAIVER", "WAIVER_REVERSE", "REFUND", "REFUND_REVERSE", "POSTING")
                    .stream().map(type -> {
                        var item = new ScmCustomerStatementItemVO();
                        item.setFactType(type);
                        if ("RED".equals(type)) item.setReceivableDelta(new BigDecimal("-12.3456"));
                        return item;
                    }).toList());
                when(service.detail(1L)).thenReturn(version);
                var response = new MockHttpServletResponse();
                new ScmCustomerStatementController(service).export(1L, response);
                try (var book = WorkbookFactory.create(new ByteArrayInputStream(response.getContentAsByteArray()))) {
                    var sheet = book.getSheetAt(0);
                    assertThat(columnValues(sheet, "类型")).containsExactly("销售应收", "退货扣减", "实际收款", "撤销收款记录",
                        "账款抵扣", "撤销抵扣", "抹零", "撤销抹零", "实际退款", "撤销退款记录", "外部账单");
                    var amount = sheet.getRow(2).getCell(columnIndex(sheet, "应收变动"));
                    assertThat(amount.getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.NUMERIC);
                    assertThat(BigDecimal.valueOf(amount.getNumericCellValue())).isEqualByComparingTo("-12.3456");
                }
            }

            @Test
            void supplierWorkbookUsesSupplierSpecificLabelsAndExplainsPartialCoverage() throws Exception {
                var service = mock(ScmSupplierStatementService.class);
                var version = new ScmSupplierStatementVO();
                version.setId(2L);
                version.setPartialScope(true);
                version.setItems(List.of("PAYABLE", "RED", "PAYMENT", "PAYMENT_REVERSE", "WRITE_OFF", "WRITE_OFF_REVERSE")
                    .stream().map(type -> {
                        var item = new ScmSupplierStatementItemVO();
                        item.setFactType(type);
                        return item;
                    }).toList());
                when(service.detail(2L)).thenReturn(version);
                var response = new MockHttpServletResponse();
                new ScmSupplierStatementController(service).export(2L, response);
                try (var book = WorkbookFactory.create(new ByteArrayInputStream(response.getContentAsByteArray()))) {
                    var sheet = book.getSheetAt(0);
                    assertThat(columnValues(sheet, "类型")).containsExactly("采购应付", "应付扣减", "实际付款", "撤销付款记录",
                        "付款抵扣", "撤销抵扣");
                    assertThat(sheet.getRow(1).getCell(columnIndex(sheet, "统计范围")).getStringCellValue())
                        .contains("本次筛选范围", "该供应商的全部付款", "不能直接核对");
                }
            }

            private List<String> columnValues(Sheet sheet, String title) {
                int column = columnIndex(sheet, title);
                var values = new ArrayList<String>();
                for (int row = 1; row <= sheet.getLastRowNum(); row++) {
                    values.add(sheet.getRow(row).getCell(column).getStringCellValue());
                }
                return values;
            }

            private int columnIndex(Sheet sheet, String title) {
                for (var cell : sheet.getRow(0)) {
                    if (title.equals(cell.getStringCellValue())) return cell.getColumnIndex();
                }
                throw new AssertionError("Missing export column: " + title);
            }
        }
    }

    @Nested
    class ReportW5PgIT extends ScmW5PgITBase {
        @Nested
        @DisplayName("Finance R0 往来概览（PG IT）")
        class ScmFinanceReportPgIT {
            private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

            @Autowired
            private ScmFinanceReportDao financeReportDao;

            @Test
            void customerCreditsAgreeAcrossOverviewFilteredDetailsAndExportAtTheCutoff() throws Exception {
                Long customer = newCustomer();
                Long owner = jdbc.queryForObject("SELECT COALESCE(MAX(employee_id), 0) + 100000 FROM t_employee", Long.class);
                jdbc.update("UPDATE xsy_customer SET seller_id = ? WHERE id = ?", owner, customer);
                Long sku = newOnShelfSku("RPT-CREDIT");
                Long firstOrder = confirmedSalesOrder(customer, sku, "1", "100");
                Long secondOrder = confirmedSalesOrder(customer, sku, "1", "50");
                LocalDate today = LocalDate.now(BUSINESS_ZONE);
                receivable("CREDIT-FIRST", firstOrder, customer, prefix, "NORMAL", null, "100", at(today.minusDays(2)), null, null);
                Long second = receivable("CREDIT-SECOND", secondOrder, customer, prefix, "NORMAL", null, "50", at(today.minusDays(1)), null, null);
                receivable("CREDIT-POOL", null, customer, prefix, "RED", null, "120", at(today), "独立退货",
                    Long.MAX_VALUE & UUID.randomUUID().getMostSignificantBits());
                receivable("CREDIT-FUTURE", null, customer, prefix, "RED", null, "30", at(today.plusDays(1)), "未来退货",
                    Long.MAX_VALUE & UUID.randomUUID().getMostSignificantBits());
                evictMybatisCache();
                assertThat(overview(overviewForm(today, today), owner).getEndingReceivableAmount()).isEqualByComparingTo("30");

                var scopes = org.mockito.Mockito.mock(com.xsy.scm.common.scope.ScmDataScopeService.class);
                var scope = org.mockito.Mockito.mock(com.xsy.scm.common.scope.ScmDataScopeContext.class);
                org.mockito.Mockito.when(scopes.resolve()).thenReturn(scope);
                org.mockito.Mockito.when(scope.getOrderSellerScope()).thenReturn(ScmValueScope.of(List.of(owner)));
                var service = new com.xsy.scm.report.service.ScmFinanceReportService(financeReportDao, scopes);
                var form = detailForm(today);
                form.setKeyword(prefix + "-CREDIT-SECOND");
                var detail = service.receivableDetails(form).getList();
                assertThat(detail).hasSize(1);
                assertThat(detail.getFirst().getReceivableId()).isEqualTo(second);
                assertThat(detail.getFirst().getOpenAmount()).isEqualByComparingTo("30");

                var export = new com.xsy.scm.report.domain.form.ScmFinanceReportExportQueryForm();
                export.setStartDate(today);
                export.setEndDate(today);
                export.setKeyword(form.getKeyword());
                var response = new org.springframework.mock.web.MockHttpServletResponse();
                new com.xsy.scm.report.controller.ScmFinanceReportController(service).exportReceivables(export, response);
                try (var workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(
                        new java.io.ByteArrayInputStream(response.getContentAsByteArray()))) {
                    assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(1);
                    assertThat(BigDecimal.valueOf(workbook.getSheetAt(0).getRow(1).getCell(7).getNumericCellValue()))
                        .isEqualByComparingTo("30");
                }
            }

            @Test
            @DisplayName("本期发生额按事件日期；期末待收 / 待付包含期初并逐单截零")
            void flowsUseTheWindowButEndingBalancesIncludeOpeningDocuments() {
                Long customerId = newCustomer();
                Long scopeOwnerId = jdbc.queryForObject(
                        "SELECT COALESCE(MAX(employee_id), 0) + 100000 FROM t_employee", Long.class);
                jdbc.update("UPDATE xsy_customer SET seller_id = ? WHERE id = ?", scopeOwnerId, customerId);
                evictMybatisCache();
                Long saleSkuId = newOnShelfSku("RPT-AR");
                Long oldOrderId = confirmedSalesOrder(customerId, saleSkuId, "10.0000", "10.0000");
                Long currentOrderId = confirmedSalesOrder(customerId, saleSkuId, "8.0000", "8.0000");
                Long overAppliedOrderId = confirmedSalesOrder(customerId, saleSkuId, "1.0000", "1.0000");
                String customerName = jdbc.queryForObject("SELECT name FROM xsy_customer WHERE id = ?", String.class, customerId);

                LocalDate today = LocalDate.now(BUSINESS_ZONE);
                OffsetDateTime oldAt = at(today.minusDays(2));
                OffsetDateTime priorWriteOffAt = at(today.minusDays(1));
                OffsetDateTime currentAt = at(today);
                Long oldReceivableId = receivable("AR-OLD", oldOrderId, customerId, customerName,
                        "NORMAL", null, "100.0000", oldAt, null, null);
                Long currentReceivableId = receivable("AR-CURRENT", currentOrderId, customerId, customerName,
                        "NORMAL", null, "80.0000", currentAt, null, null);
                Long overAppliedReceivableId = receivable("AR-OVER", overAppliedOrderId, customerId, customerName,
                        "NORMAL", null, "10.0000", currentAt, null, null);
                receivable("AR-OLD-RED", oldOrderId, customerId, customerName,
                        "RED", oldReceivableId, "20.0000", currentAt, "approved return", 901L);
                receivable("AR-CURRENT-RED", currentOrderId, customerId, customerName,
                        "RED", currentReceivableId, "10.0000", currentAt, "approved return", 902L);
                receivable("AR-OVER-RED", overAppliedOrderId, customerId, customerName,
                        "RED", overAppliedReceivableId, "20.0000", currentAt, "approved return", 903L);

                Long receiptId = receipt(customerId, customerName, "500.0000", currentAt);
                Long currentOrderWriteOffId = writeOff("WO-AR-CURRENT", "RECEIPT", receiptId, "RECEIVABLE",
                        currentReceivableId, "20.0000", "NORMAL", null, currentAt, null);
                Long oldWriteOffId = writeOff("WO-AR-OLD", "RECEIPT", receiptId, "RECEIVABLE",
                        oldReceivableId, "30.0000", "NORMAL", null, priorWriteOffAt, null);
                writeOff("WO-AR-OLD-REVERSE", "RECEIPT", receiptId, "RECEIVABLE",
                        oldReceivableId, "5.0000", "REVERSE", oldWriteOffId, currentAt, "correction");
                assertThat(currentOrderWriteOffId).isPositive();

                Long purchaseSkuId = newOnShelfSku("RPT-AP");
                Long supplierId = newPurchasableSupplier("RPT-AP", purchaseSkuId);
                String supplierName = jdbc.queryForObject("SELECT name FROM xsy_supplier WHERE id = ?", String.class, supplierId);
                PurchaseFact oldPurchase = purchaseFact("AP-OLD", supplierId, purchaseSkuId, "10.0000", "10.0000",
                        scopeOwnerId);
                PurchaseFact currentPurchase = purchaseFact("AP-CURRENT", supplierId, purchaseSkuId, "10.0000", "8.0000",
                        scopeOwnerId);
                PurchaseFact smallNetPurchase = purchaseFact("AP-SMALL", supplierId, purchaseSkuId, "1.0000", "10.0000",
                        scopeOwnerId);
                Long oldPayableId = payable("AP-OLD", oldPurchase, supplierId, supplierName, "NORMAL", null,
                        "200.0000", oldAt, null);
                Long currentPayableId = payable("AP-CURRENT", currentPurchase, supplierId, supplierName, "NORMAL", null,
                        "80.0000", currentAt, null);
                Long smallNetPayableId = payable("AP-SMALL", smallNetPurchase, supplierId, supplierName, "NORMAL", null,
                        "10.0000", currentAt, null);
                payable("AP-OLD-RED", oldPurchase, supplierId, supplierName, "RED", oldPayableId,
                        "20.0000", currentAt, "manual red");
                payable("AP-CURRENT-RED", currentPurchase, supplierId, supplierName, "RED", currentPayableId,
                        "10.0000", currentAt, "manual red");
                payable("AP-SMALL-RED", smallNetPurchase, supplierId, supplierName, "RED", smallNetPayableId,
                        "5.0000", currentAt, "manual red");

                Long paymentId = payment(supplierId, supplierName, "500.0000", currentAt);
                writeOff("WO-AP-CURRENT", "PAYMENT", paymentId, "PAYABLE", currentPayableId,
                        "20.0000", "NORMAL", null, currentAt, null);
                Long oldPayableWriteOffId = writeOff("WO-AP-OLD", "PAYMENT", paymentId, "PAYABLE", oldPayableId,
                        "50.0000", "NORMAL", null, priorWriteOffAt, null);
                writeOff("WO-AP-OLD-REVERSE", "PAYMENT", paymentId, "PAYABLE", oldPayableId,
                        "10.0000", "REVERSE", oldPayableWriteOffId, currentAt, "correction");

                ScmFinanceOverviewQueryForm currentWindow = overviewForm(today, today);
                ScmFinanceOverviewVO current = overview(currentWindow, scopeOwnerId);
                assertThat(current.getReceivableOccurredAmount()).isEqualByComparingTo("40.0000");
                assertThat(current.getReceivableWrittenOffAmount()).isEqualByComparingTo("15.0000");
                assertThat(current.getEndingReceivableAmount()).isEqualByComparingTo("105.0000");
                assertThat(current.getPayableOccurredAmount()).isEqualByComparingTo("55.0000");
                assertThat(current.getPayableWrittenOffAmount()).isEqualByComparingTo("10.0000");
                assertThat(current.getEndingPayableAmount()).isEqualByComparingTo("195.0000");

                ScmFinanceOverviewVO widerWindow = overview(overviewForm(today.minusDays(2), today), scopeOwnerId);
                assertThat(widerWindow.getReceivableOccurredAmount()).isEqualByComparingTo("140.0000");
                assertThat(widerWindow.getReceivableWrittenOffAmount()).isEqualByComparingTo("45.0000");
                assertThat(widerWindow.getPayableOccurredAmount()).isEqualByComparingTo("255.0000");
                assertThat(widerWindow.getPayableWrittenOffAmount()).isEqualByComparingTo("60.0000");
                assertThat(widerWindow.getEndingReceivableAmount()).isEqualByComparingTo(current.getEndingReceivableAmount());
                assertThat(widerWindow.getEndingPayableAmount()).isEqualByComparingTo(current.getEndingPayableAmount());

                ScmFinanceReportQueryForm detailForm = detailForm(today);
                ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(detailForm);
                ScmValueScope scope = ScmValueScope.of(List.of(scopeOwnerId));
                Page<?> receivablePage = new Page<>(1, 100);
                List<ScmFinanceReceivableDetailVO> receivables = financeReportDao
                        .receivableDetails(receivablePage, range.endAt(), detailForm, scope);
                ScmFinanceReceivableDetailVO overAppliedReceivable = receivables.stream()
                        .filter(row -> row.getReceivableId().equals(overAppliedReceivableId)).findFirst().orElseThrow();
                assertThat(overAppliedReceivable.getNetAmount()).isEqualByComparingTo("-10.0000");
                assertThat(overAppliedReceivable.getOpenAmount()).isEqualByComparingTo("0.0000");
                assertThat(overAppliedReceivable.getOverAppliedAmount()).isEqualByComparingTo("10.0000");
                assertThat(receivables.stream().map(ScmFinanceReceivableDetailVO::getOpenAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("105.0000");

                Page<?> payablePage = new Page<>(1, 100);
                List<ScmFinancePayableDetailVO> payables = financeReportDao
                        .payableDetails(payablePage, range.endAt(), detailForm, scope);
                ScmFinancePayableDetailVO smallNetPayable = payables.stream()
                        .filter(row -> row.getPayableId().equals(smallNetPayableId)).findFirst().orElseThrow();
                assertThat(smallNetPayable.getNetAmount()).isEqualByComparingTo("5.0000");
                assertThat(smallNetPayable.getOpenAmount()).isEqualByComparingTo("5.0000");
                assertThat(smallNetPayable.getOverAppliedAmount()).isEqualByComparingTo("0.0000");
                assertThat(payables.stream().map(ScmFinancePayableDetailVO::getOpenAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("195.0000");
            }

            private static OffsetDateTime at(LocalDate date) {
                return date.atTime(12, 0).atZone(BUSINESS_ZONE).toOffsetDateTime();
            }

            private ScmFinanceOverviewQueryForm overviewForm(LocalDate startDate, LocalDate endDate) {
                ScmFinanceOverviewQueryForm form = new ScmFinanceOverviewQueryForm();
                form.setStartDate(startDate);
                form.setEndDate(endDate);
                return form;
            }

            private ScmFinanceReportQueryForm detailForm(LocalDate endDate) {
                ScmFinanceReportQueryForm form = new ScmFinanceReportQueryForm();
                form.setPageNum(1L);
                form.setPageSize(100L);
                form.setStartDate(endDate.minusDays(30));
                form.setEndDate(endDate);
                return form;
            }

            private ScmFinanceOverviewVO overview(ScmFinanceOverviewQueryForm form, Long scopeOwnerId) {
                ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
                ScmValueScope scope = ScmValueScope.of(List.of(scopeOwnerId));
                return financeReportDao.overview(range.startAt(), range.endAt(), scope, scope);
            }

            private Long receivable(String suffix, Long orderId, Long customerId, String customerName, String entryType,
                    Long originalId, String amount, OffsetDateTime eventAt, String reason, Long sourceId) {
                // Keep immutable Finance facts as append-only test inserts so event dates can straddle the report window.
                String resolvedSourceType = "NORMAL".equals(entryType) ? "SALES_ORDER" : "ORDER_RETURN";
                Long resolvedSourceId = sourceId == null ? orderId : sourceId;
                return jdbc.queryForObject("INSERT INTO xsy_finance_receivable "
                                + "(receivable_no, source_type, source_id, order_id, customer_id, customer_name_snapshot, "
                                + "settlement_customer_id, settlement_customer_name_snapshot, "
                                + "entry_type, original_receivable_id, amount, event_at, reason) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                        Long.class, prefix + "-" + suffix, resolvedSourceType, resolvedSourceId, orderId, customerId,
                        customerName, customerId, customerName, entryType, originalId, new BigDecimal(amount), eventAt, reason);
            }

            private PurchaseFact purchaseFact(String suffix, Long supplierId, Long skuId, String quantity, String price,
                    Long scopeOwnerId) {
                PurchaseOrderVO order = createDraftOrder(suffix, supplierId, skuId, quantity, price);
                jdbc.update("UPDATE xsy_purchase_order SET purchaser_id = ? WHERE id = ?", scopeOwnerId, order.getId());
                evictMybatisCache();
                submitOrder(order.getId());
                PurchaseReceiptVO receipt = createReceipt(order.getId());
                return new PurchaseFact(order.getId(), receipt.getId());
            }

            private Long payable(String suffix, PurchaseFact fact, Long supplierId, String supplierName, String entryType,
                    Long originalId, String amount, OffsetDateTime eventAt, String reason) {
                // The source PO / receipt are real domain rows; the immutable header fixture supplies controlled event time.
                String resolvedSourceType = "NORMAL".equals(entryType) ? "PURCHASE_RECEIPT" : "MANUAL";
                Long sourceId = "NORMAL".equals(entryType) ? fact.receiptId() : null;
                return jdbc.queryForObject("INSERT INTO xsy_finance_payable "
                                + "(payable_no, source_type, source_id, purchase_order_id, supplier_id, "
                                + "supplier_name_snapshot, entry_type, original_payable_id, amount, event_at, reason) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                        Long.class, prefix + "-" + suffix, resolvedSourceType, sourceId, fact.purchaseOrderId(), supplierId,
                        supplierName, entryType, originalId, new BigDecimal(amount), eventAt, reason);
            }

            private Long receipt(Long customerId, String customerName, String amount, OffsetDateTime receivedAt) {
                return jdbc.queryForObject("INSERT INTO xsy_finance_receipt "
                                + "(receipt_no, customer_id, customer_name_snapshot, settlement_customer_id, "
                                + "settlement_customer_name_snapshot, amount, method, received_at, entry_type) "
                                + "VALUES (?, ?, ?, ?, ?, ?, 'BANK_TRANSFER', ?, 'NORMAL') RETURNING id",
                        Long.class, prefix + "-RC-" + UUID.randomUUID(), customerId, customerName, customerId, customerName,
                        new BigDecimal(amount), receivedAt);
            }

            private Long payment(Long supplierId, String supplierName, String amount, OffsetDateTime paidAt) {
                return jdbc.queryForObject("INSERT INTO xsy_finance_payment "
                                + "(payment_no, counterparty_type, counterparty_id, counterparty_name_snapshot, amount, "
                                + "method, paid_at, entry_type) "
                                + "VALUES (?, 'SUPPLIER', ?, ?, ?, 'BANK_TRANSFER', ?, 'NORMAL') RETURNING id",
                        Long.class, prefix + "-PM-" + UUID.randomUUID(), supplierId, supplierName,
                        new BigDecimal(amount), paidAt);
            }

            private Long writeOff(String suffix, String sourceType, Long sourceId, String targetType, Long targetId,
                    String amount, String entryType, Long reverseOfId, OffsetDateTime writtenOffAt, String reason) {
                return jdbc.queryForObject("INSERT INTO xsy_finance_write_off "
                                + "(write_off_no, source_type, source_id, target_type, target_id, amount, entry_type, "
                                + "reverse_of_id, reason, written_off_at, operator) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                        Long.class, prefix + "-" + suffix, sourceType, sourceId, targetType, targetId,
                        new BigDecimal(amount), entryType, reverseOfId, reason, writtenOffAt, "Finance R0 IT");
            }

            private record PurchaseFact(Long purchaseOrderId, Long receiptId) {
            }
        }

        @Nested

        class StatementPostingOptionPgIT {
            @Autowired
            private ScmCustomerStatementService statementService;

            @Autowired
            private FinancePostingService postingService;

            @Test
            @DisplayName("#1 不勾选过账时不出现过账行；勾选后过账行进入明细但余额规则不变")
            void postingLinesDoNotChangeBalances() {
                Long customerId = newCustomer();
                insertReceipt(customerId, "50.0000");
                postingService.add(postingForm(customerId, "30.0000"), key("add"));
                withPostingPermission(() -> balanceAssertions(customerId));
            }

            private Void balanceAssertions(Long customerId) {
                ScmCustomerStatementVO withoutPosting = statementService.freeze(statementForm(customerId, false));
                assertThat(withoutPosting.getIncludePosting()).isFalse();
                assertThat(factsOf(withoutPosting)).noneMatch("POSTING"::equals);
                assertThat(withoutPosting.getClosingReceivable()).isEqualByComparingTo("0.0000");
                assertThat(withoutPosting.getClosingUnallocated()).isEqualByComparingTo("50.0000");

                ScmCustomerStatementVO withPosting = statementService.freeze(statementForm(customerId, true));
                assertThat(withPosting.getIncludePosting()).isTrue();
                assertThat(factsOf(withPosting)).containsExactlyInAnyOrder("RECEIPT", "POSTING");
                assertThat(withPosting.getClosingReceivable()).isEqualByComparingTo("0.0000");
                assertThat(withPosting.getClosingUnallocated()).isEqualByComparingTo("50.0000");
                // 过账行的余额列与资金变动列都停在原值：它不是财务记录
                ScmCustomerStatementItemVO line = postingLine(withPosting);
                assertThat(line.getPostingAmount()).isEqualByComparingTo("30.0000");
                assertThat(line.getReceivableDelta()).isZero();
                assertThat(line.getReceiptDelta()).isZero();
                assertThat(line.getWriteOffDelta()).isZero();
                assertThat(line.getRefundDelta()).isZero();
                assertThat(line.getReceivableBalance()).isZero();
                // 过账金额真正写入数据库，明细与打印都从保存行读
                assertThat(jdbc.queryForObject(
                    "SELECT posting_amount FROM xsy_customer_statement_item WHERE statement_id = ? AND fact_type = 'POSTING'",
                    BigDecimal.class, withPosting.getId())).isEqualByComparingTo("30.0000");
                return null;
            }

            @Test
            @DisplayName("#2 作废的过账不进对账单")
            void voidedPostingsAreExcluded() {
                Long customerId = newCustomer();
                insertReceipt(customerId, "20.0000");
                FinancePostingVO posting = postingService.add(postingForm(customerId, "12.0000"), key("void-add"));
                FinancePostingVoidForm voidForm = new FinancePostingVoidForm();
                voidForm.setPostingId(posting.getPostingId());
                voidForm.setVersion(posting.getVersion());
                voidForm.setReason("录错客户");
                postingService.voidPosting(voidForm, key("void"));

                withPostingPermission(() -> {
                    ScmCustomerStatementVO statement = statementService.freeze(statementForm(customerId, true));
                    assertThat(factsOf(statement)).containsExactly("RECEIPT");
                    return null;
                });
            }

            @Test
            @DisplayName("#3 保存后的版本记住 includePosting，重新读取时规则一致")
            void frozenVersionRemembersTheOption() {
                Long customerId = newCustomer();
                insertReceipt(customerId, "10.0000");
                postingService.add(postingForm(customerId, "5.0000"), key("read-add"));

                withPostingPermission(() -> {
                    ScmCustomerStatementVO created = statementService.freeze(statementForm(customerId, true));
                    ScmCustomerStatementVO reloaded = statementService.loadForPrint(created.getId());
                    assertThat(reloaded.getIncludePosting()).isTrue();
                    assertThat(factsOf(reloaded)).containsExactlyInAnyOrder("RECEIPT", "POSTING");
                    assertThat(postingLine(reloaded).getPostingAmount()).isEqualByComparingTo("5.0000");
                    return null;
                });
            }

            @Test
            @DisplayName("#4 没有过账查询权限：勾选包含过账被拒绝，不勾选照常出单")
            void postingLinesRequirePostingPermission() {
                Long customerId = newCustomer();
                insertReceipt(customerId, "10.0000");
                try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                    stp.when(() -> StpUtil.hasPermission(FinancePermission.POSTING_QUERY)).thenReturn(false);
                    assertThatThrownBy(() -> statementService.freeze(statementForm(customerId, true)))
                        .isInstanceOf(ScmDataScopeException.class);
                    // 不包含过账时，普通对账权限不受影响
                    assertThat(factsOf(statementService.freeze(statementForm(customerId, false)))).containsExactly("RECEIPT");
                }
            }

            /**
             * 勾选「包含过账」要求 {@code scm:finance:posting:query}；IT 里没有 Sa-Token 登录上下文，
             * {@code StpUtil.hasPermission} 默认取 false，必须显式点名放行。
             */
            private <T> T withPostingPermission(Supplier<T> body) {
                try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                    stp.when(() -> StpUtil.hasPermission(FinancePermission.POSTING_QUERY)).thenReturn(true);
                    return body.get();
                }
            }

            private static List<String> factsOf(ScmCustomerStatementVO statement) {
                return statement.getItems().stream().map(ScmCustomerStatementItemVO::getFactType).toList();
            }

            private static ScmCustomerStatementItemVO postingLine(ScmCustomerStatementVO statement) {
                return statement.getItems().stream().filter(item -> "POSTING".equals(item.getFactType())).findFirst()
                    .orElseThrow(() -> new AssertionError("对账单缺少过账行"));
            }

            private ScmCustomerStatementForm statementForm(Long customerId, boolean includePosting) {
                ScmCustomerStatementForm form = new ScmCustomerStatementForm();
                // 不限定原客户：结算方就是这个客户，限定后 partialScope 会置真、未分配资金不再下结论
                form.setSettlementCustomerId(customerId);
                form.setStartDate(LocalDate.now().minusDays(1));
                form.setEndDate(LocalDate.now());
                form.setIncludePosting(includePosting);
                return form;
            }

            private FinancePostingAddForm postingForm(Long customerId, String amount) {
                FinancePostingAddForm form = new FinancePostingAddForm();
                form.setCustomerId(customerId);
                form.setDocumentDate(LocalDate.now());
                form.setAmount(amount);
                form.setTaxRate("13");
                form.setRemark(prefix + " 对账单过账");
                return form;
            }

            /**
             * 造一笔未核销收款：对账单的 RECEIPT 记录不依赖订单，用它做最小的可对账底数。
             */
            private void insertReceipt(Long customerId, String amount) {
                String receivableNo = "RC-" + UUID.randomUUID().toString().substring(0, 12);
                jdbc.update("INSERT INTO xsy_finance_receipt (receipt_no, customer_id, customer_name_snapshot, "
                    + "settlement_customer_id, settlement_customer_name_snapshot, amount, method, received_at, entry_type, "
                    + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, 'CASH', ?, 'NORMAL', ?, ?)",
                    receivableNo, customerId, prefix + " 对账客户", customerId, prefix + " 对账客户",
                    new BigDecimal(amount), OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now());
            }

            private String key(String tag) {
                return prefix + ":" + tag + ":" + UUID.randomUUID();
            }
        }
    }

    @Nested
    class ReportW6PgIT extends ScmW6PgITBase {
        @Nested

        class CustomerMonthlyProfitStatementPgIT {
            private static final AtomicLong SOURCE_IDS = new AtomicLong(System.currentTimeMillis());
            @Autowired private FinanceCustomerExpenseService expenseService;
            @Autowired private ScmCustomerMonthlyProfitService monthlyProfitService;
            private MockedStatic<StpUtil> permissions;

            @BeforeEach
            void permitReportQueries() {
                permissions = mockStatic(StpUtil.class);
                permissions.when(() -> StpUtil.hasPermission(anyString())).thenReturn(true);
            }

            @AfterEach
            void closePermissions() {
                permissions.close();
            }

            @Test
            void statementUsesReceivablePostingAndReceiptNetAmounts() {
                Long customer = newCustomer();
                OffsetDateTime now = OffsetDateTime.now(ZoneId.of("Asia/Shanghai"));
                insertReceivable(customer, "1000.0000", now);
                Long receiptId = insertReceipt(customer, "400.0000", "NORMAL", null, now);
                insertReceipt(customer, "100.0000", "REVERSE", receiptId, now);
                insertPosting(customer, "50.0000", "13.0000", month());
                addExpense(customer, "600.0000", "10.0000", "20.0000", "5.0000", "3.0000");

                var query = monthlyQuery(customer);
                var row = monthlyProfitService.query(query).getList().getFirst();
                assertThat(row.getRevenueAmount()).isEqualByComparingTo("1000.0000");
                assertThat(row.getStatementAmount()).isEqualByComparingTo("1050.0000");
                assertThat(row.getPaidAmount()).isEqualByComparingTo("300.0000");
                assertThat(row.getUnpaidAmount()).isEqualByComparingTo("750.0000");
                assertThat(row.getInvoiceAmount()).isEqualByComparingTo("1050.0000");
                assertThat(row.getPostingAmount()).isEqualByComparingTo("50.0000");
                assertThat(row.getPostingTaxAmount()).isEqualByComparingTo("6.5000");
                // 实际货款 = 对账 1050 − 过账 50 − 过账税金 6.5
                assertThat(row.getActualGoodsAmount()).isEqualByComparingTo("993.5000");
                assertThat(row.getInvoiceTaxAmount()).isEqualByComparingTo("10.5000");
                assertThat(row.getServiceFeeAmount()).isEqualByComparingTo("63.0000");
                // 对账金额 1050 − 成本 600 − 1% 税 10.5 − 6% 服务费 63 − 运费 5 − 扣点 3
                assertThat(row.getStatementProfit()).isEqualByComparingTo("368.5000");

                var summary = monthlyProfitService.summary(query);
                assertThat(summary.getStatementAmount()).isEqualByComparingTo("1050.0000");
                assertThat(summary.getPaidAmount()).isEqualByComparingTo("300.0000");
                assertThat(summary.getUnpaidAmount()).isEqualByComparingTo("750.0000");
                assertThat(summary.getPostingAmount()).isEqualByComparingTo("50.0000");
                assertThat(summary.getPostingTaxAmount()).isEqualByComparingTo("6.5000");
                assertThat(summary.getStatementProfit()).isEqualByComparingTo("368.5000");
            }

            @Test
            void voidedPostingIsExcludedAndMissingExpenseLeavesProfitEmpty() {
                Long customer = newCustomer();
                OffsetDateTime now = OffsetDateTime.now(ZoneId.of("Asia/Shanghai"));
                insertReceivable(customer, "1000.0000", now);
                insertPosting(customer, "50.0000", "13.0000", month());
                voidPosting(customer, "80.0000", "13.0000", month());

                var query = monthlyQuery(customer);
                var row = monthlyProfitService.query(query).getList().getFirst();
                assertThat(row.getPostingAmount()).isEqualByComparingTo("50.0000");
                assertThat(row.getPostingTaxAmount()).isEqualByComparingTo("6.5000");
                assertThat(row.getStatementAmount()).isEqualByComparingTo("1050.0000");
                assertThat(row.getActualGoodsAmount()).isEqualByComparingTo("993.5000");
                // 没有成本费用登记时对账利润为空，但对账 / 实付等记录列仍照常展示。
                assertThat(row.getCostMissingCount()).isEqualTo(1);
                assertThat(row.getStatementProfit()).isNull();
                assertThat(row.getInvoiceTaxAmount()).isEqualByComparingTo("10.5000");
                assertThat(row.getServiceFeeAmount()).isEqualByComparingTo("63.0000");
            }

            @Test
            void revokedPostingPermissionBlocksEveryMonthlyRead() {
                permissions.when(() -> StpUtil.hasPermission(FinancePermission.POSTING_QUERY)).thenReturn(false);
                var query = monthlyQuery(1L);
                assertThatThrownBy(() -> monthlyProfitService.query(query)).isInstanceOf(ScmDataScopeException.class);
                assertThatThrownBy(() -> monthlyProfitService.summary(query)).isInstanceOf(ScmDataScopeException.class);
                assertThatThrownBy(() -> monthlyProfitService.export(query)).isInstanceOf(ScmDataScopeException.class);
            }

            @Test
            void statementProfitSubtractsTheDisplayedRoundedFees() {
                Long customer = newCustomer();
                insertReceivable(customer, "100.0008", OffsetDateTime.now(ZoneId.of("Asia/Shanghai")));
                addExpense(customer, "80.0000", "0.0000", "0.0000", "0.0000", "0.0000");
                var query = monthlyQuery(customer);
                var row = monthlyProfitService.query(query).getList().getFirst();
                assertThat(row.getStatementPeriod()).isEqualTo(query.getStartDate() + " 至 " + query.getEndDate());
                assertThat(row.getInvoiceTaxAmount()).isEqualByComparingTo("1.0000");
                assertThat(row.getServiceFeeAmount()).isEqualByComparingTo("6.0000");
                assertThat(row.getStatementProfit()).isEqualByComparingTo("13.0008");
                assertThat(row.getStatementProfit()).isEqualByComparingTo(row.getStatementAmount()
                    .subtract(row.getSalesCostAmount()).subtract(row.getInvoiceTaxAmount()).subtract(row.getServiceFeeAmount()));
                assertThat(monthlyProfitService.summary(query).getStatementProfit()).isEqualByComparingTo("13.0008");
            }

            private void insertReceivable(Long customerId, String amount, OffsetDateTime eventAt) {
                Long sourceId = SOURCE_IDS.incrementAndGet();
                jdbc.update("INSERT INTO xsy_finance_receivable(receivable_no, source_type, source_id, order_id, customer_id,"
                        + " customer_name_snapshot, settlement_customer_id, settlement_customer_name_snapshot, entry_type,"
                        + " amount, event_at) VALUES (?, 'SALES_ORDER', ?, ?, ?, '对账客户', ?, '对账客户', 'NORMAL', ?, ?)",
                    prefix + "-AR-" + sourceId, sourceId, sourceId, customerId, customerId, new BigDecimal(amount), eventAt);
            }

            private Long insertReceipt(Long customerId, String amount, String entryType, Long reverseOfId,
                OffsetDateTime receivedAt) {
                String no = prefix + "-RC-" + SOURCE_IDS.incrementAndGet();
                jdbc.update("INSERT INTO xsy_finance_receipt(receipt_no, customer_id, customer_name_snapshot,"
                        + " settlement_customer_id, settlement_customer_name_snapshot, amount, method, received_at, entry_type,"
                        + " reverse_of_id, reason) VALUES (?, ?, '对账客户', ?, '对账客户', ?, 'BANK_TRANSFER', ?, ?, ?, ?)",
                    no, customerId, customerId, new BigDecimal(amount), receivedAt, entryType, reverseOfId,
                    "REVERSE".equals(entryType) ? "收款登记错误" : null);
                return jdbc.queryForObject("SELECT id FROM xsy_finance_receipt WHERE receipt_no = ?", Long.class, no);
            }

            private Long insertPosting(Long customerId, String amount, String taxRate, LocalDate documentDate) {
                String no = prefix + "-PO-" + SOURCE_IDS.incrementAndGet();
                jdbc.update("INSERT INTO xsy_finance_posting(posting_no, customer_id, customer_name_snapshot, document_date,"
                        + " amount, tax_rate) VALUES (?, ?, '对账客户', ?, ?, ?)",
                    no, customerId, documentDate, new BigDecimal(amount), new BigDecimal(taxRate));
                return jdbc.queryForObject("SELECT id FROM xsy_finance_posting WHERE posting_no = ?", Long.class, no);
            }

            private void voidPosting(Long customerId, String amount, String taxRate, LocalDate documentDate) {
                Long postingId = insertPosting(customerId, amount, taxRate, documentDate);
                jdbc.update("INSERT INTO xsy_finance_posting_void(posting_id, reason) VALUES (?, '过账登记错误')", postingId);
            }

            private void addExpense(Long customerId, String goodsCost, String tax, String service, String freight,
                String commission) {
                FinanceCustomerExpenseForm.Add form = new FinanceCustomerExpenseForm.Add();
                form.setCustomerId(customerId);
                form.setExpenseMonth(month());
                form.setGoodsCostAmount(goodsCost);
                form.setTaxAmount(tax);
                form.setServiceAmount(service);
                form.setFreightAmount(freight);
                form.setCommissionAmount(commission);
                form.setReason("月度对账成本费用");
                expenseService.add(form, prefix + ":expense");
            }

            private LocalDate month() {
                return LocalDate.now(ZoneId.of("Asia/Shanghai")).withDayOfMonth(1);
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
        }

        @Nested

        class PurchaseDailyReportPgIT {
            private static final LocalDate REPORT_DATE = LocalDate.of(2000, 1, 17);
            private static final long SKU_ID = 987_654_321L;
            private static final long WAREHOUSE_A = 987_654_322L;
            private static final long WAREHOUSE_B = 987_654_323L;
            private static final long PURCHASER_A = 987_654_324L;
            private static final long PURCHASER_B = 987_654_325L;

            @Autowired
            private JdbcTemplate jdbcTemplate;
            @Autowired
            private PurchaseDailyGenerationService generationService;
            @Autowired
            private PurchaseDailyQueryService queryService;
            @Autowired
            private PurchaseDailyReportDao reportDao;
            @Autowired
            private PurchaseDailyReportController reportController;

            @Test
            void aggregatesValidSubmittedOrdersInsideShanghaiDayAndKeepsUnitsSeparate() {
                order(ScmPurchaseStatusEnum.SUBMITTED, start(), "kg", "2", WAREHOUSE_A, PURCHASER_A);
                order(ScmPurchaseStatusEnum.PARTIALLY_RECEIVED, start().plusHours(8), "kg", "3", WAREHOUSE_A, PURCHASER_A);
                order(ScmPurchaseStatusEnum.RECEIVED, start().plusDays(1).minusNanos(1000), "kg", "4", WAREHOUSE_A, PURCHASER_A);
                order(ScmPurchaseStatusEnum.SHORT_CLOSED, start().plusHours(12), "箱", "5", WAREHOUSE_A, PURCHASER_A);
                order(ScmPurchaseStatusEnum.SUBMITTED, start().minusNanos(1000), "kg", "100", WAREHOUSE_A, PURCHASER_A);
                order(ScmPurchaseStatusEnum.SUBMITTED, start().plusDays(1), "kg", "100", WAREHOUSE_A, PURCHASER_A);
                order(ScmPurchaseStatusEnum.CANCELLED, start(), "kg", "100", WAREHOUSE_A, PURCHASER_A);
                Long deletedOrder = order(ScmPurchaseStatusEnum.SUBMITTED, start(), "kg", "100", WAREHOUSE_A, PURCHASER_A);
                jdbcTemplate.update("UPDATE xsy_purchase_order SET deleted = TRUE WHERE id = ?", deletedOrder);
                Long deletedItem = order(ScmPurchaseStatusEnum.SUBMITTED, start(), "kg", "100", WAREHOUSE_A, PURCHASER_A);
                jdbcTemplate.update("UPDATE xsy_purchase_order_item SET deleted = TRUE WHERE purchase_order_id = ?", deletedItem);
                evictMybatisCache();

                assertThat(generationService.generate(REPORT_DATE)).isTrue();
                List<PurchaseDailyReportVO.ProductRow> rows = products(ScmValueScope.all(), ScmValueScope.all(), null);
                assertThat(rows).hasSize(2);
                PurchaseDailyReportVO.ProductRow kilograms = rows.stream().filter(row -> "kg".equals(row.getPurchaseUnit()))
                        .findFirst().orElseThrow();
                assertThat(kilograms.getOrderCount()).isEqualTo(3);
                assertThat(kilograms.getPlannedQuantity()).isEqualByComparingTo("9");
                assertThat(kilograms.getOrderAmount()).isEqualByComparingTo("18");
                assertThat(rows.stream().filter(row -> "箱".equals(row.getPurchaseUnit())).findFirst().orElseThrow()
                        .getPlannedQuantity()).isEqualByComparingTo("5");
            }

            @Test
            void keepsEachPurchaseOrderNameAndCodeSnapshotWhenProductChangesDuringTheDay() {
                order(ScmPurchaseStatusEnum.SUBMITTED, start(), "kg", "2", WAREHOUSE_A, PURCHASER_A,
                        "SPU-OLD", "奶白菜", "SKU-OLD", "250g");
                order(ScmPurchaseStatusEnum.SUBMITTED, start().plusHours(8), "kg", "3", WAREHOUSE_A, PURCHASER_A,
                        "SPU-NEW", "上海青", "SKU-NEW", "250g");

                assertThat(generationService.generate(REPORT_DATE)).isTrue();

                List<PurchaseDailyReportVO.ProductRow> rows = products(ScmValueScope.all(), ScmValueScope.all(), null);
                assertThat(rows).hasSize(2);
                PurchaseDailyReportVO.ProductRow oldSnapshot = rows.stream()
                        .filter(row -> "奶白菜".equals(row.getProductName())).findFirst().orElseThrow();
                PurchaseDailyReportVO.ProductRow newSnapshot = rows.stream()
                        .filter(row -> "上海青".equals(row.getProductName())).findFirst().orElseThrow();
                assertThat(oldSnapshot.getSpuCode()).isEqualTo("SPU-OLD");
                assertThat(oldSnapshot.getSkuCode()).isEqualTo("SKU-OLD");
                assertThat(oldSnapshot.getOrderCount()).isEqualTo(1);
                assertThat(oldSnapshot.getPlannedQuantity()).isEqualByComparingTo("2");
                assertThat(newSnapshot.getSpuCode()).isEqualTo("SPU-NEW");
                assertThat(newSnapshot.getSkuCode()).isEqualTo("SKU-NEW");
                assertThat(newSnapshot.getOrderCount()).isEqualTo(1);
                assertThat(newSnapshot.getPlannedQuantity()).isEqualByComparingTo("3");
            }

            @Test
            @Transactional(propagation = Propagation.NOT_SUPPORTED)
            void rollsBackClaimedHeaderWhenSnapshotItemInsertFailsAndAllowsRetry() {
                List<Long> orderIds = new ArrayList<>();
                try {
                    orderIds.add(order(ScmPurchaseStatusEnum.SUBMITTED, start(), "kg", "2", WAREHOUSE_A, PURCHASER_A));
                    dropItemFailureTrigger();
                    jdbcTemplate.execute("""
                            CREATE FUNCTION xsy_test_reject_purchase_daily_item() RETURNS trigger
                            LANGUAGE plpgsql AS $$
                            BEGIN
                                RAISE EXCEPTION 'forced purchase daily item insert failure';
                            END;
                            $$
                            """);
                    jdbcTemplate.execute("""
                            CREATE TRIGGER xsy_test_reject_purchase_daily_item
                            BEFORE INSERT ON xsy_report_purchase_daily_item
                            FOR EACH ROW EXECUTE FUNCTION xsy_test_reject_purchase_daily_item()
                            """);

                    assertThatThrownBy(() -> generationService.generate(REPORT_DATE))
                            .isInstanceOf(DataAccessException.class);
                    assertThat(countDailyHeaders()).isZero();
                    assertThat(countDailyItems()).isZero();

                    dropItemFailureTrigger();
                    assertThat(generationService.generate(REPORT_DATE)).isTrue();
                    assertThat(countDailyHeaders()).isEqualTo(1);
                    assertThat(countDailyItems()).isEqualTo(1);
                } finally {
                    dropItemFailureTrigger();
                    cleanupDailyReportFixtures(orderIds);
                }
            }

            @Test
            @Transactional(propagation = Propagation.NOT_SUPPORTED)
            void concurrentGenerationCreatesOneCompleteSnapshot() throws Exception {
                List<Long> orderIds = new ArrayList<>();
                ExecutorService executor = Executors.newFixedThreadPool(2);
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch startTogether = new CountDownLatch(1);
                try {
                    orderIds.add(order(ScmPurchaseStatusEnum.SUBMITTED, start(), "kg", "2", WAREHOUSE_A, PURCHASER_A));
                    orderIds.add(order(ScmPurchaseStatusEnum.SUBMITTED, start().plusHours(1), "kg", "3", WAREHOUSE_A, PURCHASER_A));
                    Future<Boolean> first = executor.submit(() -> generateTogether(ready, startTogether));
                    Future<Boolean> second = executor.submit(() -> generateTogether(ready, startTogether));

                    assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                    startTogether.countDown();
                    assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                            .containsExactlyInAnyOrder(true, false);
                    assertThat(countDailyHeaders()).isEqualTo(1);
                    assertThat(countDailyItems()).isEqualTo(1);
                    assertThat(jdbcTemplate.queryForObject(
                            "SELECT order_count FROM xsy_report_purchase_daily_item WHERE report_date = ?",
                            Integer.class, REPORT_DATE)).isEqualTo(2);
                    assertThat(jdbcTemplate.queryForObject(
                            "SELECT planned_quantity FROM xsy_report_purchase_daily_item WHERE report_date = ?",
                            BigDecimal.class, REPORT_DATE)).isEqualByComparingTo("5");
                } finally {
                    startTogether.countDown();
                    executor.shutdownNow();
                    executor.awaitTermination(10, TimeUnit.SECONDS);
                    cleanupDailyReportFixtures(orderIds);
                }
            }

            @Test
            void repeatedExecutionPreservesSnapshotAfterSourceCancellation() throws Exception {
                Long orderId = order(ScmPurchaseStatusEnum.SUBMITTED, start(), "kg", "2", WAREHOUSE_A, PURCHASER_A);
                assertThat(generationService.generate(REPORT_DATE)).isTrue();
                OffsetDateTime generatedAt = reportDao.findGeneratedAt(REPORT_DATE);
                jdbcTemplate.update("UPDATE xsy_purchase_order SET status = 'CANCELLED', cancel_reason = '测试取消', "
                        + "cancelled_at = CURRENT_TIMESTAMP WHERE id = ?", orderId);
                evictMybatisCache();

                assertThat(generationService.generate(REPORT_DATE)).isFalse();
                assertThat(reportDao.findGeneratedAt(REPORT_DATE)).isEqualTo(generatedAt);
                assertThat(products(ScmValueScope.all(), ScmValueScope.all(), null).getFirst().getPlannedQuantity())
                        .isEqualByComparingTo("2");
                PurchaseDailyReportVO report = queryService.query(form());
                assertThat(report.getGeneratedAt()).isNotNull();
                assertThat(report.getProducts().getTotal()).isEqualTo(1);
                MockHttpServletResponse response = new MockHttpServletResponse();
                reportController.export(reportFilter(form(), PurchaseDailyQueryForm.Filter.class), response);
                assertThat(response.getContentAsByteArray()).startsWith((byte) 'P', (byte) 'K');
            }

            @Test
            void intersectsWarehouseAndPurchaserScopesBeforeAggregation() {
                order(ScmPurchaseStatusEnum.SUBMITTED, start(), "kg", "2", WAREHOUSE_A, PURCHASER_A);
                order(ScmPurchaseStatusEnum.SUBMITTED, start(), "kg", "30", WAREHOUSE_A, PURCHASER_B);
                order(ScmPurchaseStatusEnum.SUBMITTED, start(), "kg", "40", WAREHOUSE_B, PURCHASER_A);
                generationService.generate(REPORT_DATE);

                ScmValueScope warehouse = ScmValueScope.of(List.of(WAREHOUSE_A));
                ScmValueScope purchaser = ScmValueScope.of(List.of(PURCHASER_A));
                assertThat(products(warehouse, purchaser, null).getFirst().getPlannedQuantity()).isEqualByComparingTo("2");
                assertThat(products(warehouse, purchaser, WAREHOUSE_B)).isEmpty();
                assertThat(products(null, purchaser, null)).isEmpty();
                assertThat(products(warehouse, null, null)).isEmpty();
                assertThat(products(ScmValueScope.none(), purchaser, null)).isEmpty();
                assertThat(products(warehouse, ScmValueScope.none(), null)).isEmpty();
            }

            @Test
            void queryServiceKeepsPurchaserScopeWhenWarehousePermissionIsUnrestricted() {
                order(ScmPurchaseStatusEnum.SUBMITTED, start(), "kg", "2", WAREHOUSE_A, PURCHASER_A);
                order(ScmPurchaseStatusEnum.SUBMITTED, start(), "kg", "30", WAREHOUSE_A, PURCHASER_B);
                order(ScmPurchaseStatusEnum.SUBMITTED, start(), "kg", "40", WAREHOUSE_B, PURCHASER_A);
                generationService.generate(REPORT_DATE);
                RequestEmployee employee = new RequestEmployee();
                employee.setEmployeeId(PURCHASER_A);
                employee.setAdministratorFlag(false);
                XsyRequestUtil.setRequestUser(employee);
                try (MockedStatic<StpUtil> permissions = mockStatic(StpUtil.class)) {
                    permissions.when(() -> StpUtil.hasPermission(ScmDataScopeService.WAREHOUSE_ALL_PERM)).thenReturn(true);
                    PurchaseDailyQueryForm query = form();
                    query.setWarehouseId(WAREHOUSE_A);
                    PurchaseDailyReportVO result = queryService.query(query);
                    assertThat(result.getProducts().getList()).hasSize(1);
                    assertThat(result.getProducts().getList().getFirst().getPlannedQuantity()).isEqualByComparingTo("2");
                }
            }

            @Test
            void distinguishesEmptySnapshotFromMissingSnapshotAndRejectsUnfinishedDays() {
                assertThat(queryService.query(form()).getGeneratedAt()).isNull();
                assertThat(generationService.generate(REPORT_DATE)).isTrue();
                assertThat(queryService.query(form()).getGeneratedAt()).isNotNull();
                assertThat(queryService.query(form()).getProducts().getList()).isEmpty();
                LocalDate today = LocalDate.now(ScmReportTimeRangeResolver.BUSINESS_ZONE);
                assertThatThrownBy(() -> generationService.generate(today)).isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> generationService.generate(today.plusDays(1))).isInstanceOf(IllegalArgumentException.class);
            }

            private List<PurchaseDailyReportVO.ProductRow> products(ScmValueScope warehouses, ScmValueScope purchasers,
                    Long warehouseFilter) {
                PurchaseDailyQueryForm query = form();
                query.setWarehouseId(warehouseFilter);
                return reportDao.queryProducts(new Page<>(1, 100), query, warehouses, purchasers);
            }

            private PurchaseDailyQueryForm form() {
                PurchaseDailyQueryForm form = new PurchaseDailyQueryForm();
                form.setReportDate(REPORT_DATE);
                form.setPageNum(1L);
                form.setPageSize(20L);
                return form;
            }

            private OffsetDateTime start() {
                return REPORT_DATE.atStartOfDay(ScmReportTimeRangeResolver.BUSINESS_ZONE).toOffsetDateTime();
            }

            private boolean generateTogether(CountDownLatch ready, CountDownLatch startTogether) throws InterruptedException {
                ready.countDown();
                if (!startTogether.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent generation did not receive its shared start signal");
                }
                return generationService.generate(REPORT_DATE);
            }

            private int countDailyHeaders() {
                return jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM xsy_report_purchase_daily WHERE report_date = ?", Integer.class, REPORT_DATE);
            }

            private int countDailyItems() {
                return jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM xsy_report_purchase_daily_item WHERE report_date = ?", Integer.class, REPORT_DATE);
            }

            private void dropItemFailureTrigger() {
                jdbcTemplate.execute(
                        "DROP TRIGGER IF EXISTS xsy_test_reject_purchase_daily_item ON xsy_report_purchase_daily_item");
                jdbcTemplate.execute("DROP FUNCTION IF EXISTS xsy_test_reject_purchase_daily_item()");
            }

            private void cleanupDailyReportFixtures(List<Long> orderIds) {
                jdbcTemplate.update("DELETE FROM xsy_report_purchase_daily_item WHERE report_date = ?", REPORT_DATE);
                jdbcTemplate.update("DELETE FROM xsy_report_purchase_daily WHERE report_date = ?", REPORT_DATE);
                for (Long orderId : orderIds) {
                    jdbcTemplate.update("DELETE FROM xsy_purchase_order_item WHERE purchase_order_id = ?", orderId);
                    jdbcTemplate.update("DELETE FROM xsy_purchase_order WHERE id = ?", orderId);
                }
            }

            private Long order(ScmPurchaseStatusEnum status, OffsetDateTime submittedAt, String unit, String quantity,
                    long warehouseId, long purchaserId) {
                return order(status, submittedAt, unit, quantity, warehouseId, purchaserId,
                        "DAILY-SPU", "每日商品", "DAILY-SKU", "规格");
            }

            private Long order(ScmPurchaseStatusEnum status, OffsetDateTime submittedAt, String unit, String quantity,
                    long warehouseId, long purchaserId, String spuCode, String productName, String skuCode, String skuName) {
                String orderNo = "DAILY-" + UUID.randomUUID();
                boolean cancelled = status == ScmPurchaseStatusEnum.CANCELLED;
                boolean shortClosed = status == ScmPurchaseStatusEnum.SHORT_CLOSED;
                BigDecimal amount = new BigDecimal(quantity).multiply(new BigDecimal("2"));
                Long orderId = jdbcTemplate.queryForObject("""
                        INSERT INTO xsy_purchase_order (order_no, supplier_id, supplier_code_snapshot, supplier_name_snapshot,
                            warehouse_id, warehouse_code_snapshot, warehouse_name_snapshot, purchaser_id, status,
                            total_amount, submitted_at, planned_arrival_date, cancelled_at, cancel_reason,
                            short_closed_at, short_close_reason)
                        VALUES (?, 1, 'SUP', '供应商', ?, 'WH', '仓库', ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                        """, Long.class, orderNo, warehouseId, purchaserId, status.name(), amount, submittedAt, REPORT_DATE,
                        cancelled ? start() : null, cancelled ? "取消" : null,
                        shortClosed ? start() : null, shortClosed ? "短结" : null);
                jdbcTemplate.update("""
                        INSERT INTO xsy_purchase_order_item (purchase_order_id, spu_id, sku_id, spu_code_snapshot,
                            product_name_snapshot, sku_code_snapshot, sku_name_snapshot, purchase_unit_snapshot,
                            product_type_snapshot, planned_quantity, received_quantity, purchase_price, line_amount)
                        VALUES (?, 1, ?, ?, ?, ?, ?, ?, 'STANDARD', ?, 0, 2, ?)
                        """, orderId, SKU_ID, spuCode, productName, skuCode, skuName, unit, new BigDecimal(quantity), amount);
                return orderId;
            }
        }

        @Nested
        @DisplayName("P0-F 报表仓库数据范围与导出规则（PG IT）")
        class ScmReportDataScopePgIT {
            private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

            /** 每个测试仓两单：DIRECT 收货入库 4.0000 × 6.2000，另有一单待入库 3.0000 × 5.0000。 */
            private static final String DIRECT_QUANTITY = "4.0000";

            private static final String DIRECT_PRICE = "6.2000";
            /** 导出把成本写成数值单元格，不再保留 4 位小数的字符串形态。 */
            private static final String DIRECT_PRICE_CELL = "6.2";

            /** 导出单元格按数值写入，不保留 4 位小数补零。 */
            private static final String DIRECT_QUANTITY_CELL = "4.0";

            private static final String PENDING_QUANTITY = "3.0000";

            private static final String PENDING_PRICE = "5.0000";

            /** 单个新建仓库的采购承诺合计（24.8 + 15），两仓恒等式与逐仓断言都靠它。 */
            private static final BigDecimal WAREHOUSE_PURCHASE_AMOUNT = new BigDecimal("39.8000");

            private static final BigDecimal WAREHOUSE_INBOUND_COST = new BigDecimal("24.8000");

            /** xlsx 是 zip 容器，读回需要随机访问，因此导出字节先落到 JUnit 负责清理的临时目录。 */
            @TempDir
            private Path workbookDir;

            @Autowired
            private ScmReportController reportController;

            @Autowired
            private OverviewReportService overviewReportService;

            @Autowired
            private SalesReportService salesReportService;

            @Autowired
            private PurchaseReportService purchaseReportService;

            @Autowired
            private ReceiptReportService receiptReportService;

            @Autowired
            private InventoryReportService inventoryReportService;

            private Long warehouseA;
            private Long warehouseB;
            private String warehouseNameA;
            private String warehouseNameB;
            private WarehouseFacts factsA;
            private WarehouseFacts factsB;
            private Long scopedToA;
            private Long scopedToB;
            private Long scopedToBoth;
            private Long withoutAnyWarehouse;

            /** 一个仓库内的报表记录：两张已提交采购单，其中一张已入库、另一张待入库。 */
            private record WarehouseFacts(Long warehouseId, Long skuId, String skuCode, Long inboundOrderId,
                                         Long pendingOrderId, Long inboundReceiptId, Long pendingReceiptId) {
            }

            @BeforeEach
            void setUpWarehousesAndEmployees() {
                // 基类的登录上下文是 administratorFlag=true，因此下列造数不受任何范围约束。
                warehouseA = newWarehouse("RPA");
                warehouseB = newWarehouse("RPB");
                warehouseNameA = warehouseName(warehouseA);
                warehouseNameB = warehouseName(warehouseB);
                factsA = stockWarehouse(warehouseA, "A");
                factsB = stockWarehouse(warehouseB, "B");

                scopedToA = newEmployee("RPA-EMP");
                scopedToB = newEmployee("RPB-EMP");
                scopedToBoth = newEmployee("RPAB-EMP");
                withoutAnyWarehouse = newEmployee("RPNONE-EMP");
                grantWarehouseScope(scopedToA, warehouseA);
                grantWarehouseScope(scopedToB, warehouseB);
                grantWarehouseScope(scopedToBoth, warehouseA);
                grantWarehouseScope(scopedToBoth, warehouseB);
            }

            // ==================== 1. 逐页与逐表都按仓库收窄 ====================

            @Test
            @DisplayName("只授权甲仓：库存流水 / 入库 / 收货 / 待入库 / 价值 / 收发存 / 采购各维度只剩甲仓")
            void everyScopedQueryReturnsOnlyTheAuthorizedWarehouse() {
                Set<String> cost = Set.of(ScmReportAccess.COST_QUERY_PERM);

                List<InventoryReportVO.MovementRow> movements = as(scopedToA, cost,
                        () -> inventoryReportService.movementList(inventoryForm()).getList());
                assertThat(movements).as("甲仓只有 DIRECT 收货产生的那一条 PURCHASE_IN 流水")
                        .extracting(InventoryReportVO.MovementRow::getSkuCode).containsExactly(factsA.skuCode());
                assertThat(movements).allSatisfy(row -> assertThat(row.getWarehouseId()).isEqualTo(warehouseA));

                assertThat(as(scopedToA, cost, () -> receiptReportService.inboundList(receiptForm()).getList()))
                        .extracting(ReceiptReportVO.InboundRow::getSkuCode).containsExactly(factsA.skuCode());

                assertThat(as(scopedToA, cost, () -> receiptReportService.receiptList(receiptForm()).getList()))
                        .as("两张已确认收货单都是甲仓的，乙仓的一张都不给")
                        .hasSize(2)
                        .allSatisfy(row -> assertThat(row.getWarehouseName()).isEqualTo(warehouseNameA))
                        .noneSatisfy(row -> assertThat(row.getWarehouseName()).isEqualTo(warehouseNameB));

                assertThat(as(scopedToA, cost, () -> receiptReportService.pendingPutawayList(receiptForm()).getList()))
                        .extracting(ReceiptReportVO.PendingPutawayRow::getReceiptNo)
                        .containsExactly(receiptNo(factsA.pendingReceiptId()));

                assertThat(as(scopedToA, cost, () -> inventoryReportService.valueList(inventoryForm()).getList()))
                        .allSatisfy(row -> assertThat(row.getWarehouseId()).isEqualTo(warehouseA))
                        .extracting(InventoryReportVO.ValueRow::getSkuCode).containsExactly(factsA.skuCode());

                List<InventoryReportVO.FlowSummaryRow> flows = as(scopedToA, cost,
                        () -> inventoryReportService.flowSummary(inventoryForm()).getList());
                assertThat(flows).hasSize(1);
                assertThat(flows.getFirst().getWarehouseId()).isEqualTo(warehouseA);
                assertThat(flows.getFirst().getPurchaseInQuantity()).isEqualByComparingTo(DIRECT_QUANTITY);

                PurchaseReportVO.Overview purchaseOverview = as(scopedToA, cost,
                        () -> purchaseReportService.overview(purchaseForm()));
                assertThat(purchaseOverview.getSubmittedOrderCount()).isEqualTo(2L);
                assertThat(purchaseOverview.getSubmittedAmount()).isEqualByComparingTo(WAREHOUSE_PURCHASE_AMOUNT);
                assertThat(purchaseOverview.getConfirmedReceiptCount()).isEqualTo(2L);
                assertThat(purchaseOverview.getPendingPutawayReceiptCount()).isEqualTo(1L);
                assertThat(purchaseOverview.getPurchaseInCostAmount()).as("只有 DIRECT 那一笔入了账")
                        .isEqualByComparingTo(WAREHOUSE_INBOUND_COST);

                ReportOverviewVO overview = as(scopedToA, cost, () -> overviewReportService.overview(overviewForm()));
                assertThat(overview.getSubmittedPurchaseAmount()).isEqualByComparingTo(WAREHOUSE_PURCHASE_AMOUNT);
                assertThat(overview.getPurchaseInCostAmount()).isEqualByComparingTo(WAREHOUSE_INBOUND_COST);
                assertThat(overview.getInventoryBookValue()).isNotNull();
                assertThat(overview.getStockedSkuCount()).isEqualTo(1L);

                assertThat(as(scopedToA, cost, () -> purchaseReportService.byProduct(purchaseForm()).getList()))
                        .as("按商品维度也只剩甲仓的 SKU")
                        .extracting(PurchaseReportVO.ProductRow::getSkuCode).containsExactly(factsA.skuCode());
                assertThat(as(scopedToA, cost, () -> purchaseReportService.bySupplier(purchaseForm()).getList()))
                        .hasSize(1);
                assertThat(as(scopedToA, cost, () -> purchaseReportService.byPurchaser(purchaseForm()).getList()))
                        .hasSize(1);
                assertThat(as(scopedToA, cost, () -> purchaseReportService.itemList(purchaseForm()).getList()))
                        .hasSize(2)
                        .allSatisfy(row -> assertThat(row.getWarehouseName()).isEqualTo(warehouseNameA));
                assertThat(as(scopedToA, cost, () -> purchaseReportService.priceTrend(purchaseForm())))
                        .as("价格波动也按仓库收窄：甲仓只有自己的 SKU")
                        .extracting(PurchaseReportVO.PriceTrendPoint::getSkuId).containsOnly(factsA.skuId());
                assertThat(as(scopedToA, cost,
                        () -> purchaseReportService.topSupplierInbound(purchaseForm()))).hasSize(1);
                // 损耗两条语句共用 lossMovementWhere 片段：本仓没有损耗流水，但带 IN (?) 的 SQL
                // 仍必须在真实库里可解析可执行（本模块的可执行性门禁比行数断言更值钱）。
                assertThatCode(() -> as(scopedToA, cost, () -> {
                    inventoryReportService.lossList(inventoryForm());
                    return inventoryReportService.lossSummary(inventoryForm());
                })).doesNotThrowAnyException();
            }

            // ==================== 2. 聚合恒等：互斥的两半之和等于并集 ====================

            @Test
            @DisplayName("聚合恒等：甲仓金额 + 乙仓金额 = 双仓授权金额，且逐日轴与指标卡同一套规则")
            void scopedAmountsAddUpToUnionScope() {
                Set<String> cost = Set.of(ScmReportAccess.COST_QUERY_PERM);
                BigDecimal onlyA = as(scopedToA, cost, () -> purchaseReportService.overview(purchaseForm()))
                        .getSubmittedAmount();
                BigDecimal onlyB = as(scopedToB, cost, () -> purchaseReportService.overview(purchaseForm()))
                        .getSubmittedAmount();
                BigDecimal union = as(scopedToBoth, cost, () -> purchaseReportService.overview(purchaseForm()))
                        .getSubmittedAmount();

                assertThat(onlyA).isEqualByComparingTo(WAREHOUSE_PURCHASE_AMOUNT);
                assertThat(onlyB).isEqualByComparingTo(WAREHOUSE_PURCHASE_AMOUNT);
                assertThat(union).isEqualByComparingTo(onlyA.add(onlyB));

                // 成本金额同样可加：它坐在 inventory_movement 的派生表里，是「改数字」风险最高的那一处
                BigDecimal costA = as(scopedToA, cost, () -> purchaseReportService.overview(purchaseForm()))
                        .getPurchaseInCostAmount();
                BigDecimal costB = as(scopedToB, cost, () -> purchaseReportService.overview(purchaseForm()))
                        .getPurchaseInCostAmount();
                BigDecimal costUnion = as(scopedToBoth, cost, () -> purchaseReportService.overview(purchaseForm()))
                        .getPurchaseInCostAmount();
                assertThat(costUnion).isEqualByComparingTo(costA.add(costB));

                // 条数恒等：范围谓词漏到维度 JOIN 上时金额仍可能可加，条数不会
                long movementsA = as(scopedToA, cost, () -> inventoryReportService.movementList(inventoryForm()))
                        .getTotal();
                long movementsB = as(scopedToB, cost, () -> inventoryReportService.movementList(inventoryForm()))
                        .getTotal();
                long movementsUnion = as(scopedToBoth, cost, () -> inventoryReportService.movementList(inventoryForm()))
                        .getTotal();
                assertThat(movementsA).isEqualTo(1L);
                assertThat(movementsUnion).isEqualTo(movementsA + movementsB).isGreaterThan(movementsA);

                // 指标卡与趋势/每日统计共用一条 SQL：同一范围下两边那个数必须相等
                List<ReportDailyStatVO> days = as(scopedToA, cost, () -> overviewReportService.dailyStat(overviewForm()));
                BigDecimal axisPurchaseAmount = days.stream().map(ReportDailyStatVO::getSubmittedPurchaseAmount)
                        .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
                assertThat(axisPurchaseAmount).isEqualByComparingTo(
                        as(scopedToA, cost, () -> overviewReportService.overview(overviewForm()))
                                .getSubmittedPurchaseAmount());

                // 台账恒等式（余额 = 本仓流水方向净额）在收口后仍然成立：范围只读，不改账本
                assertLedgerBalanced(warehouseA, factsA.skuId());
                assertLedgerBalanced(warehouseB, factsB.skuId());
            }

            // ==================== 3. 导出与页面同一套范围 ====================

            @Test
            @DisplayName("Excel 导出与页面查询同一范围：导出的工作簿里有甲仓无乙仓")
            void exportedWorkbookUsesTheSameWarehouseScopeAsThePage() throws Exception {
                Set<String> cost = Set.of(ScmReportAccess.COST_QUERY_PERM);

                assertThat(export(scopedToA, cost, response -> reportController.exportMovement(reportFilter(inventoryForm(), ScmInventoryReportQueryForm.Filter.class), response)))
                        .contains(factsA.skuCode()).doesNotContain(factsB.skuCode());
                assertThat(export(scopedToA, cost, response -> reportController.exportInbound(reportFilter(receiptForm(), ScmReceiptReportQueryForm.Filter.class), response)))
                        .contains(factsA.skuCode()).doesNotContain(factsB.skuCode());
                assertThat(export(scopedToA, cost, response -> reportController.exportReceipt(reportFilter(receiptForm(), ScmReceiptReportQueryForm.Filter.class), response)))
                        .contains(receiptNo(factsA.inboundReceiptId()))
                        .doesNotContain(receiptNo(factsB.inboundReceiptId()));
                assertThat(export(scopedToA, cost,
                        response -> reportController.exportPurchaseItem(reportFilter(purchaseForm(), ScmPurchaseReportQueryForm.Filter.class), response)))
                        .contains(factsA.skuCode()).doesNotContain(factsB.skuCode());
                assertThat(export(scopedToA, cost,
                        response -> reportController.exportPurchaseProduct(reportFilter(purchaseForm(), ScmPurchaseReportQueryForm.Filter.class), response)))
                        .contains(factsA.skuCode()).doesNotContain(factsB.skuCode());
                assertThat(export(scopedToA, cost,
                        response -> reportController.exportInventoryValue(reportFilter(inventoryForm(), ScmInventoryReportQueryForm.Filter.class), response)))
                        .contains(factsA.skuCode()).doesNotContain(factsB.skuCode());

                // 授权双仓的人导出两仓：证明收窄来自范围本身，不是导出被写死成半张表
                assertThat(export(scopedToBoth, cost,
                        response -> reportController.exportMovement(reportFilter(inventoryForm(), ScmInventoryReportQueryForm.Filter.class), response)))
                        .contains(factsA.skuCode(), factsB.skuCode());
            }

            @Test
            @DisplayName("无成本权限时导出的成本单元格是空、接口字段是 null，绝不写成 0")
            void exportMasksCostCellsInsteadOfWritingZero() throws Exception {
                String withCost = export(scopedToA, Set.of(ScmReportAccess.COST_QUERY_PERM),
                        response -> reportController.exportMovement(reportFilter(inventoryForm(), ScmInventoryReportQueryForm.Filter.class), response));
                String withoutCost = export(scopedToA, Set.of(),
                        response -> reportController.exportMovement(reportFilter(inventoryForm(), ScmInventoryReportQueryForm.Filter.class), response));

                assertThat(withCost).contains(DIRECT_PRICE_CELL);
                assertThat(withoutCost).doesNotContain(DIRECT_PRICE_CELL)
                        .as("抹除只抹成本列，数量列仍是记录").contains(DIRECT_QUANTITY_CELL, factsA.skuCode());
                assertThat(as(scopedToA, Set.of(),
                        () -> inventoryReportService.movementList(inventoryForm()).getList().getFirst().getUnitCost()))
                        .isNull();
            }

            // ==================== 4. 无授权范围：空结果，不是一堆 0 ====================

            @Test
            @DisplayName("无任何仓库授权：列表是空分页、汇总是 null，绝不给出别的仓的数字")
            void callerWithoutWarehouseGrantSeesEmptyResultNotZeros() throws Exception {
                Set<String> cost = Set.of(ScmReportAccess.COST_QUERY_PERM);

                PageResult<InventoryReportVO.MovementRow> movements = as(withoutAnyWarehouse, cost,
                        () -> inventoryReportService.movementList(inventoryForm()));
                assertThat(movements.getList()).isEmpty();
                assertThat(movements.getTotal()).isZero();
                assertThat(movements.getEmptyFlag()).as("空分页形状与正常分页一致，前端不必另写分支").isTrue();

                assertThat(as(withoutAnyWarehouse, cost, () -> inventoryReportService.lossList(inventoryForm()).getList()))
                        .isEmpty();
                assertThat(as(withoutAnyWarehouse, cost, () -> inventoryReportService.valueList(inventoryForm()).getList()))
                        .isEmpty();
                assertThat(as(withoutAnyWarehouse, cost, () -> inventoryReportService.flowSummary(inventoryForm()).getList()))
                        .isEmpty();
                assertThat(as(withoutAnyWarehouse, cost, () -> receiptReportService.receiptList(receiptForm()).getList()))
                        .isEmpty();
                assertThat(as(withoutAnyWarehouse, cost, () -> receiptReportService.inboundList(receiptForm()).getList()))
                        .isEmpty();
                assertThat(as(withoutAnyWarehouse, cost,
                        () -> receiptReportService.pendingPutawayList(receiptForm()).getList())).isEmpty();
                assertThat(as(withoutAnyWarehouse, cost, () -> purchaseReportService.itemList(purchaseForm()).getList()))
                        .isEmpty();
                assertThat(as(withoutAnyWarehouse, cost, () -> purchaseReportService.byProduct(purchaseForm()).getList()))
                        .isEmpty();
                assertThat(as(withoutAnyWarehouse, cost, () -> purchaseReportService.bySupplier(purchaseForm()).getList()))
                        .isEmpty();
                assertThat(as(withoutAnyWarehouse, cost, () -> purchaseReportService.byPurchaser(purchaseForm()).getList()))
                        .isEmpty();
                assertThat(as(withoutAnyWarehouse, cost, () -> purchaseReportService.priceTrend(purchaseForm())))
                        .isEmpty();
                assertThat(as(withoutAnyWarehouse, cost, () -> purchaseReportService.topSupplierInbound(purchaseForm())))
                        .isEmpty();

                PurchaseReportVO.Overview emptyPurchase = as(withoutAnyWarehouse, cost,
                        () -> purchaseReportService.overview(purchaseForm()));
                assertThat(emptyPurchase.getSubmittedAmount()).as("无授权仓库时采购承诺是未知，不是 0").isNull();
                assertThat(emptyPurchase.getSubmittedOrderCount()).isNull();
                assertThat(emptyPurchase.getConfirmedReceiptCount()).isNull();
                assertThat(emptyPurchase.getPendingPutawayReceiptCount()).isNull();

                InventoryReportVO.LossSummary emptyLoss = as(withoutAnyWarehouse, cost,
                        () -> inventoryReportService.lossSummary(inventoryForm()));
                assertThat(emptyLoss.getStocktakeLossCount()).isNull();
                assertThat(emptyLoss.getLossReportCount()).isNull();
                assertThat(emptyLoss.getTotalLossCostAmount()).isNull();

                ReportOverviewVO overview = as(withoutAnyWarehouse, cost,
                        () -> overviewReportService.overview(overviewForm()));
                assertThat(overview.getSubmittedPurchaseAmount()).isNull();
                assertThat(overview.getPurchaseInCostAmount()).isNull();
                assertThat(overview.getInventoryBookValue()).isNull();
                assertThat(overview.getStockedSkuCount()).isNull();
                // 日期轴上每一行的采购列按同一规则抹除，否则卡片是 — 而折线是 0
                assertThat(as(withoutAnyWarehouse, cost, () -> overviewReportService.dailyStat(overviewForm())))
                        .allSatisfy(row -> assertThat(row.getSubmittedPurchaseAmount()).isNull());

                String workbook = export(withoutAnyWarehouse, cost,
                        response -> reportController.exportMovement(reportFilter(inventoryForm(), ScmInventoryReportQueryForm.Filter.class), response));
                assertThat(workbook).doesNotContain(factsA.skuCode(), factsB.skuCode())
                        .as("工作簿仍能写出（表头在），但没有任何一行数据").contains("发生时间");
            }

            // ==================== 5. 销售侧没有仓库记录，故不被仓库范围收窄 ====================

            @Test
            @DisplayName("销售侧无仓库列：仓库范围为空的人也照常看到销售额，可见性只由页面权限承担")
            void salesReportsStayUnscopedBecauseOrdersCarryNoWarehouse() {
                Long customerId = newCustomer();
                Long skuId = newOnShelfSku("RPS");
                confirmedSalesOrder(customerId, skuId, "2.0000", "2.0000");

                ScmSalesReportQueryForm form = page(new ScmSalesReportQueryForm());
                form.setStartDate(start());
                form.setEndDate(end());
                form.setCustomerId(customerId);

                for (Long employeeId : List.of(scopedToA, withoutAnyWarehouse)) {
                    assertThat(as(employeeId, Set.of(), () -> salesReportService.byCustomer(form).getList()))
                            .as("员工 %s 只是没有仓库授权，销售记录本身没有仓库列", employeeId)
                            .anySatisfy(row -> assertThat(row.getCustomerId()).isEqualTo(customerId));
                }
                assertThat(as(withoutAnyWarehouse, Set.of(), () -> salesReportService.itemList(form).getList())).hasSize(1);
                assertThat(as(withoutAnyWarehouse, Set.of(), () -> salesReportService.byProduct(form).getList())).hasSize(1);
                assertThat(as(withoutAnyWarehouse, Set.of(), () -> salesReportService.bySeller(form).getList()))
                        .as("按销售员维度也不引入仓库替代维度").isNotEmpty();

                // 概览里不受仓库范围影响的两个销售指标仍要有值，否则说明范围把不该收的也收了
                ReportOverviewVO overview = as(withoutAnyWarehouse, Set.of(),
                        () -> overviewReportService.overview(overviewForm()));
                assertThat(overview.getConfirmedOrderCount()).isNotNull();
                assertThat(overview.getConfirmedOrderAmount()).isNotNull();
            }

            // ==================== 6. 超管与「查看全部仓库」权限 ====================

            @Test
            @DisplayName("administratorFlag=true 不受影响：两个仓的流水同页可见，数字未被收窄")
            void administratorStillSeesEveryWarehouse() {
                List<InventoryReportVO.MovementRow> rows = inventoryReportService.movementList(inventoryForm()).getList();
                assertThat(rows).extracting(InventoryReportVO.MovementRow::getSkuCode)
                        .contains(factsA.skuCode(), factsB.skuCode());
                assertThat(rows).extracting(InventoryReportVO.MovementRow::getWarehouseId)
                        .contains(warehouseA, warehouseB);

                PurchaseReportVO.Overview overview = purchaseReportService.overview(purchaseForm());
                assertThat(overview.getSubmittedAmount())
                        .as("超管至少看到两个测试仓之和").isGreaterThanOrEqualTo(WAREHOUSE_PURCHASE_AMOUNT.multiply(
                                BigDecimal.valueOf(2)));
                assertThat(overview.getPurchaseInCostAmount()).isNull();
                // 无 Sa-Token 上下文时成本校验失败时拒绝操作，与范围无关：证明两件事互不隐含
                assertThat(overview.getSubmittedAmount()).isNotNull();
            }

            @Test
            @DisplayName("「查看全部仓库」是显式权限：没有授权行但持该权限者可见两仓")
            void warehouseAllPermissionIsAnExplicitScopeNotADefault() {
                assertThat(as(withoutAnyWarehouse, Set.of(),
                        () -> inventoryReportService.movementList(inventoryForm()).getList()))
                        .as("没权限也没授权行 ⇒ 空").isEmpty();
                assertThat(as(withoutAnyWarehouse, Set.of(ScmDataScopeService.WAREHOUSE_ALL_PERM),
                        () -> inventoryReportService.movementList(inventoryForm()).getList()))
                        .extracting(InventoryReportVO.MovementRow::getSkuCode)
                        .contains(factsA.skuCode(), factsB.skuCode());
            }

            // ==================== 夹具 ====================

            /**
             * 在一个仓库里造齐报表要用的记录：一单 DIRECT 收货确认（产生 {@code PURCHASE_IN} 流水与余额），
             * 另加一单 {@code WAREHOUSE_CONFIRM} 收货确认（只挂待入库、不写库存），
             * 于是收货页有 2 行、入库页 1 行、待入库 1 行、流水与余额各 1 行。
             */
            private WarehouseFacts stockWarehouse(Long warehouseId, String tag) {
                Long skuId = newOnShelfSku(tag);
                Long supplierId = newPurchasableSupplier(tag, skuId);

                PurchaseOrderVO inbound = purchaseOrderService.create(
                        orderForm(supplierId, warehouseId, skuId, DIRECT_QUANTITY, DIRECT_PRICE),
                        prefix + ":" + tag + ":inbound:po");
                PurchaseReceiptVO inboundReceipt = submittedOrderReceipt(inbound.getId());
                confirmReceipt(inboundReceipt.getId(), DIRECT_QUANTITY);

                PurchaseOrderVO pending = purchaseOrderService.create(
                        orderForm(supplierId, warehouseId, skuId, PENDING_QUANTITY, PENDING_PRICE),
                        prefix + ":" + tag + ":pending:po");
                submitOrder(pending.getId());
                PurchaseReceiptVO pendingReceipt = createWarehouseConfirmReceipt(pending.getId(), tag);
                confirmReceipt(pendingReceipt.getId(), PENDING_QUANTITY);

                return new WarehouseFacts(warehouseId, skuId, skuCodeOf(skuId), inbound.getId(), pending.getId(),
                        inboundReceipt.getId(), pendingReceipt.getId());
            }

            /** {@code WAREHOUSE_CONFIRM} 的收货单：确认后 {@code putaway_status} 保持 PENDING，不写库存。 */
            private PurchaseReceiptVO createWarehouseConfirmReceipt(Long orderId, String tag) {
                PurchaseReceiptCreateForm form = new PurchaseReceiptCreateForm();
                form.setPurchaseOrderId(orderId);
                form.setReceiptMode(ScmReceiptModeEnum.WAREHOUSE_CONFIRM.name());
                form.setRemark("报表数据范围 IT 待入库");
                return purchaseReceiptService.create(form, prefix + ":wc:" + tag + ":" + orderId);
            }

            /**
             * 真实员工行，{@code administrator_flag} 必须 FALSE（裁决第 5 条）。
             */
            private Long newEmployee(String suffix) {
                String loginName = (prefix + "-" + suffix).toUpperCase(Locale.ROOT);
                jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                                + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                        UUID.randomUUID().toString().replace("-", ""), loginName, "$argon2id$it-placeholder",
                        "报表范围" + suffix);
                Long employeeId = jdbc.queryForObject(
                        "SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, loginName);
                assertThat(jdbc.queryForObject(
                        "SELECT administrator_flag FROM t_employee WHERE employee_id = ?", Boolean.class, employeeId))
                        .as("正式业务角色的验收账号禁止超管位").isFalse();
                return employeeId;
            }

            private void grantWarehouseScope(Long employeeId, Long warehouseId) {
                jdbc.update("INSERT INTO xsy_employee_warehouse_scope (employee_id, warehouse_id) VALUES (?, ?)",
                        employeeId, warehouseId);
            }

            private void loginAs(Long employeeId) {
                RequestEmployee employee = new RequestEmployee();
                employee.setEmployeeId(employeeId);
                employee.setActualName("报表数据范围 IT");
                employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                employee.setAdministratorFlag(false);
                employee.setDepartmentId(1L);
                XsyRequestUtil.setRequestUser(employee);
            }

            /** 以某个员工 + 一组功能权限执行一段逻辑；未点名的权限码取 false（校验失败时拒绝操作）。不可嵌套。 */
            private <T> T as(Long employeeId, Set<String> permissions, Supplier<T> body) {
                loginAs(employeeId);
                try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                    permissions.forEach(p -> stp.when(() -> StpUtil.hasPermission(p)).thenReturn(true));
                    // 同一批语句会换身份重跑，MyBatis 一级缓存必须清，否则断言读回的是上一个人的结果
                    evictMybatisCache();
                    return body.get();
                }
            }

            /** 跑一个导出端点，并把工作簿（zip 容器）里的全部 XML 拼成一段文本供包含性断言。 */
            private String export(Long employeeId, Set<String> permissions, ExportCall call) throws Exception {
                MockHttpServletResponse response = new MockHttpServletResponse();
                as(employeeId, permissions, () -> {
                    try {
                        call.run(response);
                    } catch (Exception e) {
                        throw new IllegalStateException("导出端点执行失败", e);
                    }
                    return null;
                });
                byte[] bytes = response.getContentAsByteArray();
                assertThat(bytes.length).as("零行结果也必须写出工作簿容器，而不是一个空响应").isGreaterThan(2);
                assertThat(new String(bytes, 0, 2, StandardCharsets.ISO_8859_1)).as("导出的必须是 xlsx").isEqualTo("PK");
                return workbookText(bytes);
            }

            interface ExportCall {
                void run(jakarta.servlet.http.HttpServletResponse response) throws Exception;
            }

            /**
             * 按文本断言「某个编码在 / 不在工作簿里」，比引入 Excel 读回模型更省，也不需要知道列在第几格 ——
             * 本类只关心哪些仓的行进了这张表。
             *
             * <p>必须走 {@link ZipFile} 而不是 {@link ZipInputStream}：FastExcel 写出的 entry 带 data
             * descriptor，本地文件头里的 size 先落 0、真长度写在数据之后，流式解析器按本地头判定就会抛
             * {@code invalid entry size}。{@code ZipFile} 读的是中央目录，那里才是写完的真实长度；
             * 代价是它需要随机访问，所以先落成临时文件。
             */
            private String workbookText(byte[] bytes) throws IOException {
                Path workbook = workbookDir.resolve("export.xlsx");
                Files.write(workbook, bytes);
                StringBuilder text = new StringBuilder();
                try (ZipFile zip = new ZipFile(workbook.toFile(), StandardCharsets.UTF_8)) {
                    Enumeration<? extends ZipEntry> entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        if (!entry.isDirectory()) {
                            try (InputStream content = zip.getInputStream(entry)) {
                                text.append(new String(content.readAllBytes(), StandardCharsets.UTF_8));
                            }
                        }
                    }
                }
                return text.toString();
            }

            private String receiptNo(Long receiptId) {
                return jdbc.queryForObject("SELECT receipt_no FROM xsy_purchase_receipt WHERE id = ?", String.class,
                        receiptId);
            }

            private String warehouseName(Long warehouseId) {
                return jdbc.queryForObject("SELECT name FROM xsy_warehouse WHERE id = ?", String.class, warehouseId);
            }

            private LocalDate end() {
                return LocalDate.now(BUSINESS_ZONE);
            }

            private LocalDate start() {
                return end().minusDays(29);
            }

            private <T extends PageParam> T page(T form) {
                form.setPageNum(1L);
                form.setPageSize(100L);
                return form;
            }

            private ScmOverviewReportQueryForm overviewForm() {
                ScmOverviewReportQueryForm form = new ScmOverviewReportQueryForm();
                form.setStartDate(start());
                form.setEndDate(end());
                return form;
            }

            private ScmPurchaseReportQueryForm purchaseForm() {
                ScmPurchaseReportQueryForm form = page(new ScmPurchaseReportQueryForm());
                form.setStartDate(start());
                form.setEndDate(end());
                return form;
            }

            private ScmReceiptReportQueryForm receiptForm() {
                ScmReceiptReportQueryForm form = page(new ScmReceiptReportQueryForm());
                form.setStartDate(start());
                form.setEndDate(end());
                return form;
            }

            private ScmInventoryReportQueryForm inventoryForm() {
                ScmInventoryReportQueryForm form = page(new ScmInventoryReportQueryForm());
                form.setStartDate(start());
                form.setEndDate(end());
                return form;
            }
        }

        @Nested
        @DisplayName("Finance R0 报表中心（PG IT）")
        class ScmReportPgIT {
            private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

            @Autowired
            private JdbcTemplate jdbc;

            @Autowired
            private ReportDao reportDao;

            @Autowired
            private com.xsy.scm.report.controller.ScmReportController reportController;

            @Autowired
            private OverviewReportService overviewReportService;

            @Autowired
            private SalesReportService salesReportService;

            @Autowired
            private PurchaseReportService purchaseReportService;

            @Autowired
            private ReceiptReportService receiptReportService;

            @Autowired
            private InventoryReportService inventoryReportService;

            private final String tag = "RPT" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();

            /** 覆盖近 30 天，既含夹具时间也含库中既有记录。 */
            private LocalDate end() {
                return LocalDate.now(BUSINESS_ZONE);
            }

            private LocalDate start() {
                return end().minusDays(29);
            }

            private OffsetDateTime at(LocalDate day) {
                return day.atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
            }

            private OffsetDateTime insideRange() {
                return end().atTime(9, 30).atZone(BUSINESS_ZONE).toOffsetDateTime();
            }

            private <T extends PageParam> T page(T form) {
                form.setPageNum(1L);
                form.setPageSize(20L);
                return form;
            }

            // ==================== 1. 全部报表 SQL 真跑 ====================

            @Test
            @DisplayName("五条销售维度 + 三个 TOP + 概览与库存各维度：全部 SQL 在真实库上可执行")
            void everyReportStatementExecutes() {
                runEveryQuery(false);
            }

            /**
             * 带齐全部可选筛选再跑一遍：每个 {@code <if>} 分支都会往 SQL 里追加一段谓词，
             * 而谓词可以引用只在部分查询里 JOIN 过的表。首轮只跑空筛选时，
             * 「按客户维度漏了分类 JOIN」这类缺陷是发现不了的（它正是被逐分支渲染的门禁抓到的）。
             */
            @Test
            @DisplayName("带齐全部可选筛选：每个 if 分支都能在真实库上解析并执行")
            void everyReportStatementExecutesWithAllFilters() {
                runEveryQuery(true);
            }

            private void runEveryQuery(boolean withFilters) {
                Long rootCategoryId = withFilters ? anyRootCategoryId() : null;

                ScmSalesReportQueryForm sales = page(new ScmSalesReportQueryForm());
                sales.setStartDate(start());
                sales.setEndDate(end());
                if (withFilters) {
                    sales.setKeyword(tag);
                    sales.setOrderSource("ADMIN");
                    sales.setCategoryId(rootCategoryId);
                }

                ScmOverviewReportQueryForm overview = overviewForm();
                ScmPurchaseReportQueryForm purchase = purchaseForm();
                ScmReceiptReportQueryForm receipt = receiptForm();
                ScmInventoryReportQueryForm inventory = inventoryForm();
                if (withFilters) {
                    overview.setKeyword(tag);
                    overview.setOrderSource("ADMIN");
                    purchase.setStatus("RECEIVED");
                    purchase.setKeyword(tag);
                    receipt.setReceiptMode("WAREHOUSE_CONFIRM");
                    receipt.setPutawayStatus("COMPLETED");
                    receipt.setKeyword(tag);
                    inventory.setMovementType("PURCHASE_IN");
                    inventory.setSourceDocumentType("PURCHASE_RECEIPT_ITEM");
                    inventory.setKeyword(tag);
                }

                assertThatCode(() -> {
                    overviewReportService.overview(overview);
                    overviewReportService.dailyStat(overview);

                    salesReportService.byProduct(sales);
                    salesReportService.topProduct(sales);
                    salesReportService.byCategory(sales);
                    salesReportService.topCategory(sales);
                    salesReportService.byCustomer(sales);
                    salesReportService.topCustomer(sales);
                    salesReportService.bySeller(sales);
                    salesReportService.itemList(sales);

                    purchaseReportService.overview(purchase);
                    purchaseReportService.byProduct(purchase);
                    purchaseReportService.bySupplier(purchase);
                    purchaseReportService.topSupplierInbound(purchase);
                    purchaseReportService.byPurchaser(purchase);
                    purchaseReportService.itemList(purchase);
                    purchaseReportService.priceTrend(purchase);

                    receiptReportService.receiptList(receipt);
                    receiptReportService.inboundList(receipt);
                    receiptReportService.pendingPutawayList(receipt);

                    inventoryReportService.movementList(inventory);
                    inventoryReportService.lossSummary(inventory);
                    inventoryReportService.lossList(inventory);
                    inventoryReportService.valueList(inventory);
                    inventoryReportService.flowSummary(inventory);
                }).doesNotThrowAnyException();
            }

            /** 一级分类：它只能靠 {@code cat2 / cat3} 谓词命中，因此是分类筛选最有代表性的一条。 */
            private Long anyRootCategoryId() {
                List<Long> ids = jdbc.queryForList(
                        "SELECT id FROM xsy_product_category WHERE deleted = FALSE AND level = 1 ORDER BY id LIMIT 1",
                        Long.class);
                return ids.isEmpty() ? null : ids.get(0);
            }

            private ScmOverviewReportQueryForm overviewForm() {
                ScmOverviewReportQueryForm form = new ScmOverviewReportQueryForm();
                form.setStartDate(start());
                form.setEndDate(end());
                return form;
            }

            private ScmPurchaseReportQueryForm purchaseForm() {
                ScmPurchaseReportQueryForm form = page(new ScmPurchaseReportQueryForm());
                form.setStartDate(start());
                form.setEndDate(end());
                return form;
            }

            private ScmReceiptReportQueryForm receiptForm() {
                ScmReceiptReportQueryForm form = page(new ScmReceiptReportQueryForm());
                form.setStartDate(start());
                form.setEndDate(end());
                return form;
            }

            private ScmInventoryReportQueryForm inventoryForm() {
                ScmInventoryReportQueryForm form = page(new ScmInventoryReportQueryForm());
                form.setStartDate(start());
                form.setEndDate(end());
                return form;
            }

            // ==================== 2. 销售规则：只统计已确认订单 ====================

            @Test
            @DisplayName("销售统计：PENDING 不计、CONFIRMED 计，且一单多行不放大退款")
            void salesReportCountsOnlyConfirmedOrders() {
                Long customerId = 900_000_001L;
                Long orderId = insertSalesOrder("PENDING", null, customerId);

                // 同一状态、同一时间轴下：待确认订单不得出现在任何销售统计里
                assertThat(confirmedOrderCountOf(customerId)).isZero();
                assertThat(salesSettlementAmountOf(customerId)).isNull();

                // 确认之后：同一行数据翻成 CONFIRMED + confirmed_at 落在区间内即被统计
                jdbc.update("UPDATE xsy_sales_order SET status = 'CONFIRMED', confirmed_at = ? WHERE id = ?",
                        insideRange(), orderId);
                assertThat(confirmedOrderCountOf(customerId)).isEqualTo(1L);
                assertThat(salesSettlementAmountOf(customerId)).isEqualByComparingTo("100.0000");

                // 第二行 + 一笔 PENDING 退款 + 一笔 COMPLETED 退款：
                // 订单金额按行求和，退款按订单聚合一次，因此两行不会把退款算两遍
                insertSalesItem(orderId, new BigDecimal("50.0000"));
                insertRefund(orderId, customerId, "PENDING", null);
                insertRefund(orderId, customerId, "COMPLETED", insideRange());
                assertThat(salesSettlementAmountOf(customerId)).isEqualByComparingTo("150.0000");
                assertThat(customerRowOf(customerId).getCompletedRefundAmount()).isEqualByComparingTo("7.0000");
                assertThat(customerRowOf(customerId).getOrderCount()).isEqualTo(1L);
                assertThat(customerRowOf(customerId).getSkuKindCount()).isEqualTo(2L);
            }

            @Test
            @DisplayName("销售成交均价：没称重的行按订购量计入数量；分母为 0 时返回 null，不是 0")
            void avgPriceFallsBackToOrderedQuantityAndIsNullOnZeroDenominator() {
                Long customerId = 900_000_002L;
                Long orderId = insertSalesOrder("CONFIRMED", insideRange(), customerId);
                // 非标行确认时还没称重：实发量为空、结算额按订购量暂计（此处故意给 0 便于比对）
                jdbc.update("UPDATE xsy_sales_order_item i SET actual_quantity = NULL, settlement_line_amount = 0 "
                        + "FROM xsy_sales_order o WHERE i.order_id = o.id AND o.id = ?", orderId);
                evictMybatisCache();

                SalesReportVO.ProductRow unweighed = salesProductRowOf(orderId);
                assertThat(unweighed).isNotNull();
                // 金额里有这一行，数量里也必须有一行：否则成交均价会被算高
                assertThat(unweighed.getConfirmedQuantity()).isEqualByComparingTo("10.0000");
                assertThat(unweighed.getAvgTransactionPrice()).isEqualByComparingTo("0.0000");
                // 分母为 0 的分支由 SQL 的 NULLIF 兜底：订购量有 CHECK > 0，正常数据走不到，这里不为它造非法行
            }

            @Test
            @DisplayName("商品销售额占比：所有匹配行做分母、分页不改变份额，累计占比最终归一")
            void salesProductContributionUsesFullFilteredDatasetBeforePaging() {
                Long orderId = insertSalesOrder("CONFIRMED", insideRange(), 900_000_033L);
                insertSalesItem(orderId, new BigDecimal("50.0000"));

                ScmSalesReportQueryForm form = page(new ScmSalesReportQueryForm());
                form.setStartDate(start());
                form.setEndDate(end());
                form.setKeyword(tag + "-商品");
                form.setPageSize(1L);
                evictMybatisCache();

                SalesReportVO.ProductRow first = salesReportService.byProduct(form).getList().get(0);
                assertThat(first.getSettlementAmount()).isEqualByComparingTo("100.0000");
                assertThat(first.getSalesAmountSharePercent()).isEqualByComparingTo("66.6667");
                assertThat(first.getCumulativeSalesAmountSharePercent()).isEqualByComparingTo("66.6667");

                form.setPageNum(2L);
                evictMybatisCache();
                SalesReportVO.ProductRow second = salesReportService.byProduct(form).getList().get(0);
                assertThat(second.getSettlementAmount()).isEqualByComparingTo("50.0000");
                assertThat(second.getSalesAmountSharePercent()).isEqualByComparingTo("33.3333");
                assertThat(second.getCumulativeSalesAmountSharePercent()).isEqualByComparingTo("100.0000");
            }

            // ==================== 3. 采购规则：只统计提交后的采购记录 ====================

            @Test
            @DisplayName("采购统计：CANCELLED 不计，SUBMITTED 计入且业务日期按 submitted_at")
            void purchaseReportCountsOnlySubmittedOrders() {
                Long supplierId = 900_000_011L;
                Long orderId = insertPurchaseOrder("CANCELLED", null, supplierId);

                assertThat(purchaseSubmittedAmountOf(supplierId)).isEqualByComparingTo("0.0000");

                jdbc.update("UPDATE xsy_purchase_order SET status = 'SUBMITTED', submitted_at = ? WHERE id = ?",
                        insideRange(), orderId);
                assertThat(purchaseSubmittedAmountOf(supplierId)).isEqualByComparingTo("88.0000");

                // 取消单即便保留 submitted_at 也必须被排除：状态与时间是两个独立条件
                insertPurchaseOrder("CANCELLED", insideRange(), supplierId);
                assertThat(purchaseSubmittedAmountOf(supplierId)).isEqualByComparingTo("88.0000");
            }

            // ==================== 4. 日期与导出守卫 ====================

            @Test
            @DisplayName("日期跨度超过 366 天必须 41111，缺日期必须 40000")
            void dateRangeGuardsRejectUnboundedQueries() {
                // 上限两侧各取一点：只断言「抛异常」无法区分跨度守卫与其他校验，
                // 因此同时证明 366 天（含首尾）是合法的。
                ScmSalesReportQueryForm justInside = page(new ScmSalesReportQueryForm());
                justInside.setStartDate(end().minusDays(365));
                justInside.setEndDate(end());
                assertThatCode(() -> salesReportService.byProduct(justInside)).doesNotThrowAnyException();

                ScmSalesReportQueryForm tooWide = page(new ScmSalesReportQueryForm());
                tooWide.setStartDate(start().minusDays(400));
                tooWide.setEndDate(end());
                assertThatThrownBy(() -> salesReportService.byProduct(tooWide))
                        .isInstanceOf(ScmBusinessException.class);

                ScmSalesReportQueryForm reversed = page(new ScmSalesReportQueryForm());
                reversed.setStartDate(end());
                reversed.setEndDate(start());
                assertThatThrownBy(() -> salesReportService.byProduct(reversed)).isInstanceOf(ScmBusinessException.class);

                ScmSalesReportQueryForm missing = page(new ScmSalesReportQueryForm());
                assertThatThrownBy(() -> salesReportService.byProduct(missing)).isInstanceOf(ScmBusinessException.class);
            }

            // ==================== 5. 权限与菜单不脱节 ====================

            @Test
            @DisplayName("控制器声明的每个权限码都在 t_menu 里存在且只存在一次，并授给 SUPER_ADMIN")
            void declaredPermissionsExistInMenuExactlyOnce() {
                List<String> declared = new java.util.ArrayList<>();
                for (var method : ScmReportController.class.getDeclaredMethods()) {
                    var permission = method.getAnnotation(SaCheckPermission.class);
                    if (permission == null) {
                        continue;
                    }
                    assertThat(java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                            .as("ScmReportController#%s 非 public 却带 @SaCheckPermission", method.getName())
                            .isTrue();
                    declared.addAll(List.of(permission.value()));
                }

                assertThat(declared).contains("scm:report:overview:query", "scm:report:sales:query",
                        "scm:report:purchase:query", "scm:report:inventory:query", "scm:report:cost:query",
                        "scm:report:export");

                for (String perm : declared.stream().distinct().toList()) {
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM t_menu WHERE api_perms = ?", Integer.class, perm))
                            .as("权限码 %s 必须在 t_menu.api_perms 中恰好出现一次", perm).isEqualTo(1);
                    assertThat(jdbc.queryForObject("""
                            SELECT count(*) FROM t_role_menu rm
                            JOIN t_menu m ON m.menu_id = rm.menu_id
                            WHERE rm.role_id = 1 AND m.api_perms = ?""", Integer.class, perm))
                            .as("权限码 %s 必须已授给 SUPER_ADMIN(role_id=1)", perm).isEqualTo(1);
                }
            }

            // ==================== 6. 成本字段校验失败时拒绝操作 ====================

            @Test
            @DisplayName("无成本权限时流水与入库的成本字段为 null，不回落成 0")
            void costFieldsAreMaskedWhenPermissionAbsent() {
                Page<Object> page = new Page<>(1, 5);
                // 直连 DAO 时必须显式给范围：这里要的是「未抹除的原始映射」，与仓库范围无关，
                // 因此传 all()，而不是留 null —— null 在 SQL 侧是恒假谓词（校验失败时拒绝操作）。
                List<InventoryReportVO.MovementRow> rows = reportDao.movementList(page, at(start()),
                        at(end().plusDays(1)), List.of("PURCHASE_IN"), inventoryForm(), ScmValueScope.all());
                // 走 DAO 才能看到未抹除的原始映射；服务层在无登录上下文时必须给 null
                inventoryReportService.movementList(inventoryForm()).getList()
                        .forEach(row -> assertThat(row.getUnitCost()).isNull());
                inventoryReportService.lossSummary(inventoryForm());
                assertThat(overviewReportService.overview(overviewForm()).getInventoryBookValue()).isNull();

                // 方向由流水类型派生，不由报表自建清单
                rows.forEach(row -> assertThat(row.getMovementType()).isNotBlank());
            }

            // ==================== 7. 导出真的能写出 xlsx ====================

            /**
             * 逐个调用 11 个导出端点并检查字节流。
             *
             * <p>这一组断言的存在理由很具体：FastExcel 在<b>写出时</b>才按单元格类型找 Converter，
             * 时间/金额字段没有转换器时 HTTP 200 已经发出去、异常只体现在下载的字节里，
             * 光测查询服务发现不了（首轮就是这样：浏览器导出报 500 才暴露）。
             */
            @Test
            @DisplayName("11 个 Excel 导出都能写出合法 xlsx（含时间列与金额列）")
            void everyExportWritesARealWorkbook() throws Exception {
                ScmSalesReportQueryForm sales = page(new ScmSalesReportQueryForm());
                sales.setStartDate(start());
                sales.setEndDate(end());
                ScmPurchaseReportQueryForm purchase = purchaseForm();
                ScmReceiptReportQueryForm receipt = receiptForm();
                ScmInventoryReportQueryForm inventory = inventoryForm();

                // 导出列白名单会按成本权限过滤列；IT 线程没有 Sa-Token 上下文，这里显式给出权限，
                // 让导出走「有成本列」的完整路径（无权限分支由 ScmExportColumnSelectionTest 覆盖）。
                try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                    stp.when(() -> StpUtil.hasPermission(anyString())).thenReturn(true);
                    assertWorkbook("销售按商品", r -> reportController.exportSalesProduct(reportFilter(sales, ScmSalesReportQueryForm.Filter.class), r));
                    assertWorkbook("销售按客户", r -> reportController.exportSalesCustomer(reportFilter(sales, ScmSalesReportQueryForm.Filter.class), r));
                    assertWorkbook("销售订单明细", r -> reportController.exportSalesItem(reportFilter(sales, ScmSalesReportQueryForm.Filter.class), r));
                    assertWorkbook("采购按商品", r -> reportController.exportPurchaseProduct(reportFilter(purchase, ScmPurchaseReportQueryForm.Filter.class), r));
                    assertWorkbook("采购按供应商", r -> reportController.exportPurchaseSupplier(reportFilter(purchase, ScmPurchaseReportQueryForm.Filter.class), r));
                    assertWorkbook("采购明细", r -> reportController.exportPurchaseItem(reportFilter(purchase, ScmPurchaseReportQueryForm.Filter.class), r));
                    assertWorkbook("收货明细", r -> reportController.exportReceipt(reportFilter(receipt, ScmReceiptReportQueryForm.Filter.class), r));
                    assertWorkbook("入库明细", r -> reportController.exportInbound(reportFilter(receipt, ScmReceiptReportQueryForm.Filter.class), r));
                    assertWorkbook("库存流水", r -> reportController.exportMovement(reportFilter(inventory, ScmInventoryReportQueryForm.Filter.class), r));
                    assertWorkbook("损耗分析", r -> reportController.exportLoss(reportFilter(inventory, ScmInventoryReportQueryForm.Filter.class), r));
                    assertWorkbook("当前库存价值", r -> reportController.exportInventoryValue(reportFilter(inventory, ScmInventoryReportQueryForm.Filter.class), r));
                }
            }

            /** 每个导出单独一个 response：共用一个 response 会让前一个的内容被后一次写出混进来。 */
            private void assertWorkbook(String label, WorkbookCall call) throws Exception {
                var response = new org.springframework.mock.web.MockHttpServletResponse();
                call.run(response);
                byte[] bytes = response.getContentAsByteArray();
                assertThat(bytes.length).as("%s 必须写出非空内容", label).isGreaterThan(1024);
                // xlsx 是 zip 容器：PK 头是「真的写出了工作簿」最廉价的证明
                assertThat(new String(bytes, 0, 2, java.nio.charset.StandardCharsets.ISO_8859_1))
                        .as("%s 导出的必须是 xlsx", label).isEqualTo("PK");
                assertThat(response.getContentType()).as("%s 必须声明 xlsx 媒体类型", label)
                        .contains("spreadsheetml");
                assertThat(response.getHeader("Content-Disposition")).as("%s 文件名必须由服务端给出", label)
                        .contains("filename");
            }

            interface WorkbookCall {
                void run(jakarta.servlet.http.HttpServletResponse response) throws Exception;
            }

            // ==================== 夹具 ====================

            /**
             * 同一次测试里 SKU 与退款单必须互不重复：
             * {@code uk_order_refund_return_active} 对 return_id 有活动唯一索引，用固定值插第二笔会直接撞索引，
             * 而 SKU 相同会让「SKU 种类数 = 2」这条断言失去意义。
             */
            private final java.util.concurrent.atomic.AtomicLong fixtureSeq = new java.util.concurrent.atomic.AtomicLong();

            @Test
            void customerRefundsFollowActualReturnedItemsAndOrderFiltersWithoutDuplicatingMoney() {
                Long customer = 900_900_100L + fixtureSeq.incrementAndGet();
                Long firstOrder = insertSalesOrder("CONFIRMED", insideRange(), customer);
                insertSalesItem(firstOrder, new BigDecimal("100"));
                var itemIds = jdbc.queryForList("SELECT id FROM xsy_sales_order_item WHERE order_id = ? ORDER BY id", Long.class, firstOrder);
                Long vegetable = itemIds.get(0);
                Long meat = itemIds.get(1);
                jdbc.update("UPDATE xsy_sales_order_item SET product_name_snapshot = ? WHERE id = ?", tag + "蔬菜", vegetable);
                jdbc.update("UPDATE xsy_sales_order_item SET product_name_snapshot = ? WHERE id = ?", tag + "肉类", meat);
                jdbc.update("UPDATE xsy_sales_order SET seller_id = 7 WHERE id = ?", firstOrder);
                // 蔬菜原价100承担50优惠，批准退回2元时退款净额为1元，肉类退回5元。
                String allocations = "[{\"orderItemId\":" + vegetable + ",\"baseAmount\":100,\"discountAmount\":50},"
                    + "{\"orderItemId\":" + meat + ",\"baseAmount\":100,\"discountAmount\":0}]";
                jdbc.update("INSERT INTO xsy_order_discount(sales_order_id, base_amount, discount_amount, allocations, created_by) "
                    + "VALUES (?, 200, 50, ?::jsonb, 'report-test')", firstOrder, allocations);
                itemRefund(firstOrder, customer, List.of(vegetable, meat), List.of("2", "5"), "6");

                Long otherOrder = insertSalesOrder("CONFIRMED", insideRange(), customer);
                jdbc.update("UPDATE xsy_sales_order SET seller_id = 8, order_source = 'MALL' WHERE id = ?", otherOrder);
                Long otherMeat = jdbc.queryForObject("SELECT id FROM xsy_sales_order_item WHERE order_id = ?", Long.class, otherOrder);
                jdbc.update("UPDATE xsy_sales_order_item SET product_name_snapshot = ? WHERE id = ?", tag + "肉类", otherMeat);
                itemRefund(otherOrder, customer, List.of(otherMeat), List.of("3"), "3");
                evictMybatisCache();

                var form = page(new com.xsy.scm.report.domain.form.ScmSalesReportQueryForm());
                form.setStartDate(start());
                form.setEndDate(end());
                form.setCustomerId(customer);
                assertThat(salesReportService.byCustomer(form).getList().getFirst().getCompletedRefundAmount()).isEqualByComparingTo("9");
                form.setKeyword(tag + "蔬菜");
                assertThat(salesReportService.byCustomer(form).getList().getFirst().getCompletedRefundAmount()).isEqualByComparingTo("1");
                form.setKeyword(tag + "肉类");
                assertThat(salesReportService.byCustomer(form).getList().getFirst().getCompletedRefundAmount()).isEqualByComparingTo("8");
                form.setKeyword(null);
                form.setOrderSource("ADMIN");
                assertThat(salesReportService.byCustomer(form).getList().getFirst().getCompletedRefundAmount()).isEqualByComparingTo("6");
                form.setOrderSource(null);
                form.setSellerId(8L);
                assertThat(salesReportService.byCustomer(form).getList().getFirst().getCompletedRefundAmount()).isEqualByComparingTo("3");
            }

            private void itemRefund(Long orderId, Long customerId, List<Long> items, List<String> amounts, String refundAmount) {
                long seq = fixtureSeq.incrementAndGet();
                long returnId = 900_000_300L + seq;
                jdbc.update("INSERT INTO xsy_order_refund(refund_no, return_id, order_id, customer_id, refund_amount, status, completed_at) "
                    + "VALUES (?, ?, ?, ?, ?, 'COMPLETED', ?)", tag + "-RF-items-" + seq, returnId, orderId, customerId,
                    new BigDecimal(refundAmount), insideRange());
                for (int index = 0; index < items.size(); index++) {
                    BigDecimal amount = new BigDecimal(amounts.get(index));
                    jdbc.update("INSERT INTO xsy_order_return_item(return_id, order_item_id, sku_id, sku_name_snapshot, spec_name_snapshot, "
                        + "sale_unit_snapshot, requested_quantity, approved_quantity, locked_unit_price, approved_amount) "
                        + "SELECT ?, id, sku_id, product_name_snapshot, spec_name_snapshot, sale_unit_snapshot, 1, 1, ?, ? "
                        + "FROM xsy_sales_order_item WHERE id = ?", returnId, amount, amount, items.get(index));
                }
            }

            private Long insertSalesOrder(String status, OffsetDateTime confirmedAt, Long customerId) {
                String orderNo = tag + "-SO-" + fixtureSeq.incrementAndGet();
                jdbc.update("""
                        INSERT INTO xsy_sales_order(order_no, customer_id, customer_code_snapshot, customer_name_snapshot,
                                                settlement_customer_id, settlement_customer_name_snapshot,
                                                order_source, status, settle_mode_snapshot, ordered_total_amount,
                                                settlement_total_amount, confirmed_at)
                        VALUES (?, ?, ?, ?, ?, ?, 'ADMIN', ?, 'INDEPENDENT', 0, 0, ?)""",
                        orderNo, customerId, tag + "-CODE", tag + "-客户", customerId, tag + "-客户", status, confirmedAt);
                Long orderId = jdbc.queryForObject("SELECT id FROM xsy_sales_order WHERE order_no = ?", Long.class,
                        orderNo);
                insertSalesItem(orderId, new BigDecimal("100.0000"));
                return orderId;
            }

            private void insertSalesItem(Long orderId, BigDecimal settlementLineAmount) {
                long sku = 900_000_100L + fixtureSeq.incrementAndGet();
                jdbc.update("""
                        INSERT INTO xsy_sales_order_item(order_id, spu_id, sku_id, spu_code_snapshot, product_name_snapshot,
                                                     sku_code_snapshot, spec_name_snapshot, sale_unit_snapshot,
                                                     product_type_snapshot, ordered_quantity, actual_quantity,
                                                     draft_unit_price, draft_price_source, manual_price_override,
                                                     locked_unit_price, locked_price_source, ordered_line_amount,
                                                     settlement_line_amount)
                        VALUES (?, 1, ?, ?, ?, ?, '5kg', 'kg', 'STANDARD', 10, 10, 10, 'MARKET', FALSE,
                                10, 'MARKET', 100, ?)""",
                        orderId, sku, tag + "-SPU", tag + "-商品", "SKU-" + sku, settlementLineAmount);
            }

            private void insertRefund(Long orderId, Long customerId, String status, OffsetDateTime completedAt) {
                long seq = fixtureSeq.incrementAndGet();
                jdbc.update("""
                        INSERT INTO xsy_order_refund(refund_no, return_id, order_id, customer_id, refund_amount, status,
                                                 completed_at)
                        VALUES (?, ?, ?, ?, 7.0000, ?, ?)""",
                        tag + "-RF-" + seq, 900_000_300L + seq, orderId, customerId, status, completedAt);
            }

            private Long insertPurchaseOrder(String status, OffsetDateTime submittedAt, Long supplierId) {
                // ck_purchase_order_cancel_reason 要求 CANCELLED 必须同带取消原因，因此取消夹具一次性写全，
                // 这也正好证明「已提交后又取消」的单保留了 submitted_at 仍不进统计。
                boolean cancelled = "CANCELLED".equals(status);
                String orderNo = tag + "-PO-" + supplierId + "-" + status + "-" + fixtureSeq.incrementAndGet();
                jdbc.update("""
                        INSERT INTO xsy_purchase_order(order_no, supplier_id, supplier_code_snapshot, supplier_name_snapshot,
                                                   warehouse_id, warehouse_code_snapshot, warehouse_name_snapshot,
                                                   status, total_amount, submitted_at, planned_arrival_date,
                                                   cancelled_at, cancel_reason)
                        VALUES (?, ?, ?, ?, 1, 'WH001', '默认仓库', ?, 88.0000, ?, ?, ?, ?)""",
                        orderNo, supplierId, tag + "-SC", tag + "-供应商", status, submittedAt, end(),
                        cancelled ? insideRange() : null, cancelled ? "报表测试取消" : null);
                Long orderId = jdbc.queryForObject("SELECT id FROM xsy_purchase_order WHERE order_no = ?", Long.class, orderNo);
                jdbc.update("""
                        INSERT INTO xsy_purchase_order_item(purchase_order_id, spu_id, sku_id, spu_code_snapshot,
                                                        product_name_snapshot, sku_code_snapshot, sku_name_snapshot,
                                                        purchase_unit_snapshot, product_type_snapshot, planned_quantity,
                                                        received_quantity, purchase_price, line_amount)
                        VALUES (?, 1, 1, ?, ?, ?, ?, 'kg', 'STANDARD', 10, 0, 8.8000, 88.0000)""",
                        orderId, tag + "-SPU", tag + "-商品", "SKU-1", tag + "-SKU");
                return orderId;
            }

            private long confirmedOrderCountOf(Long customerId) {
                evictMybatisCache();
                ScmSalesReportQueryForm form = page(new ScmSalesReportQueryForm());
                form.setStartDate(start());
                form.setEndDate(end());
                form.setCustomerId(customerId);
                return overviewRows(form).getConfirmedOrderCount();
            }

            private BigDecimal salesSettlementAmountOf(Long customerId) {
                SalesReportVO.CustomerRow row = customerRowOf(customerId);
                return row == null ? null : row.getSettlementAmount();
            }

            private com.xsy.scm.report.domain.vo.ReportOverviewVO overviewRows(
                    ScmSalesReportQueryForm ignored) {
                ScmOverviewReportQueryForm form = overviewForm();
                form.setCustomerId(ignored.getCustomerId());
                return overviewReportService.overview(form);
            }

            private SalesReportVO.CustomerRow customerRowOf(Long customerId) {
                evictMybatisCache();
                ScmSalesReportQueryForm form = page(new ScmSalesReportQueryForm());
                form.setStartDate(start());
                form.setEndDate(end());
                form.setCustomerId(customerId);
                return salesReportService.byCustomer(form).getList().stream()
                        .filter(r -> customerId.equals(r.getCustomerId()))
                        .findFirst().orElse(null);
            }

            private SalesReportVO.ProductRow salesProductRowOf(Long orderId) {
                evictMybatisCache();
                Long skuId = jdbc.queryForObject("SELECT sku_id FROM xsy_sales_order_item WHERE order_id = ? LIMIT 1",
                        Long.class, orderId);
                ScmSalesReportQueryForm form = page(new ScmSalesReportQueryForm());
                form.setStartDate(start());
                form.setEndDate(end());
                form.setKeyword(tag + "-商品");
                return salesReportService.byProduct(form).getList().stream()
                        .filter(r -> skuId.equals(r.getSkuId()))
                        .findFirst().orElse(null);
            }

            private BigDecimal purchaseSubmittedAmountOf(Long supplierId) {
                evictMybatisCache();
                ScmPurchaseReportQueryForm form = page(new ScmPurchaseReportQueryForm());
                form.setStartDate(start());
                form.setEndDate(end());
                form.setSupplierId(supplierId);
                PurchaseReportVO.Overview overview = purchaseReportService.overview(form);
                return overview == null ? BigDecimal.ZERO : overview.getSubmittedAmount();
            }
        }
    }

    @Nested
    @SpringBootTest(classes = XsyApplication.class, properties = {
            "project.log-directory=" + PgITPaths.DEFAULT_LOG_DIR,
            "file.storage.local.upload-path=" + PgITPaths.DEFAULT_UPLOAD_PATH,
            "file.storage.local.url-prefix=http://127.0.0.1:18082",
            "logging.level.root=WARN"})
    class ScmOrderExceptionReadDaoPgIT {
        @Autowired
        private ScmOrderExceptionReadDao dao;

        @Test
        void emptyScopeCannotExposeExceptionsAndBothStockProjectionPathsCompile() {
            var query = new ScmOrderExceptionQueryForm();
            var start = OffsetDateTime.now().minusDays(7);
            var end = OffsetDateTime.now().plusDays(1);
            var types = Arrays.stream(ScmOrderExceptionTypeEnum.values()).map(Enum::name).toList();
            assertThat(dao.query(new Page<>(1, 20, false), query, start, end, null, types, false, false))
                    .isEmpty();
            assertThat(dao.query(new Page<>(1, 20, false), query, start, end, null, types, false, true))
                    .isEmpty();
            assertThat(dao.summary(query, start, end, null, types, false)).isEmpty();
        }
    }

    @Nested
    class InventoryLossUnitSummaryTest {
        @Test
        void formatsEachMovementTypeAndUnitIndependently() {
            var units = List.of(
                    row("LOSS_REPORT", "箱", "2"),
                    row("LOSS_REPORT", "kg", "12.5000"),
                    row("STOCKTAKE_LOSS", "kg", "0.5000"));
            assertThat(InventoryReportService.lossQuantityText(units, "LOSS_REPORT"))
                    .isEqualTo("2.0000 箱、12.5000 kg");
            assertThat(InventoryReportService.lossQuantityText(units, "STOCKTAKE_LOSS"))
                    .isEqualTo("0.5000 kg");
            assertThat(InventoryReportService.lossQuantityText(units, "STOCKTAKE_GAIN")).isNull();
        }

        @Test
        void missingUnitIsNotSilentlyMergedWithKnownUnit() {
            assertThat(InventoryReportService.lossQuantityText(
                    List.of(row("LOSS_REPORT", null, "3.0000")), "LOSS_REPORT"))
                    .isEqualTo("3.0000 单位缺失");
        }

        private static InventoryReportVO.LossUnitRow row(String kind, String unit, String quantity) {
            var result = new InventoryReportVO.LossUnitRow();
            result.setMovementType(kind);
            result.setUnit(unit);
            result.setQuantity(new BigDecimal(quantity));
            return result;
        }
    }

    @Nested
    @DisplayName("非分页筛选与分页校验各自生效")
    class FilterValidation {
        @Test
        void httpOverviewAcceptsDatesButPagedQueryStillRequiresPagination() throws Exception {
            var purchase = mock(PurchaseReportService.class);
            var controller = new ScmReportController(mock(OverviewReportService.class), mock(SalesReportService.class),
                purchase, mock(ReceiptReportService.class), mock(InventoryReportService.class));
            var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
            String dates = "{\"startDate\":\"2026-11-01\",\"endDate\":\"2026-11-06\"}";
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/scm/report/purchase/overview").contentType("application/json").content(dates))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(0));
            var captured = org.mockito.ArgumentCaptor.forClass(ScmPurchaseReportQueryForm.class);
            verify(purchase).overview(captured.capture());
            assertThat(captured.getValue().getStartDate()).isEqualTo(LocalDate.of(2026, 11, 1));
            assertThat(captured.getValue().getPageSize()).isEqualTo(1L);
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/scm/report/purchase/product").contentType("application/json").content(dates))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/scm/report/purchase/overview").contentType("application/json").content("{}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
            org.mockito.Mockito.verifyNoMoreInteractions(purchase);
        }

        @Test
        void httpExportReadsAllMatchingRowsRegardlessOfClientPage() throws Exception {
            var purchase = mock(PurchaseReportService.class);
            var first = new PurchaseReportVO.SupplierRow();
            first.setSupplierName("供应商甲");
            var second = new PurchaseReportVO.SupplierRow();
            second.setSupplierName("供应商乙");
            var page = new PageResult<PurchaseReportVO.SupplierRow>();
            page.setList(List.of(first, second));
            when(purchase.bySupplier(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
                ScmPurchaseReportQueryForm query = invocation.getArgument(0);
                assertThat(query.getPageNum()).isEqualTo(1L);
                assertThat(query.getPageSize()).isEqualTo(com.xsy.scm.report.support.ScmReportExportGuard.PROBE_PAGE_SIZE);
                return page;
            });
            var controller = new ScmReportController(mock(OverviewReportService.class), mock(SalesReportService.class),
                purchase, mock(ReceiptReportService.class), mock(InventoryReportService.class));
            var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
            var result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/scm/report/purchase/supplier/export").contentType("application/json")
                .content("{\"startDate\":\"2026-11-01\",\"endDate\":\"2026-11-06\",\"pageNum\":9,\"pageSize\":1}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
            try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
                var sheet = workbook.getSheetAt(0);
                assertThat(sheet.getLastRowNum()).isEqualTo(2);
                assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("供应商甲");
                assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("供应商乙");
            }
        }

        @Test
        void purchaseOverviewDoesNotRequireClientPagination() throws Exception {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            var form = mapper.readValue("""
                {"startDate":"2026-11-01","endDate":"2026-11-06","pageNum":9,"pageSize":9000}
                """, ScmPurchaseReportQueryForm.Filter.class);
            try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
                assertThat(factory.getValidator().validate(form)).isEmpty();
            }
            assertThat(form.getStartDate()).isEqualTo(LocalDate.of(2026, 11, 1));
            assertThat(form.getPageNum()).isEqualTo(1L);
            assertThat(form.getPageSize()).isEqualTo(1L);
        }

        @Test
        void normalPageStillRequiresBothPageFields() {
            var form = new ScmPurchaseReportQueryForm();
            form.setStartDate(LocalDate.of(2026, 11, 1));
            form.setEndDate(LocalDate.of(2026, 11, 6));
            try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
                assertThat(factory.getValidator().validate(form)).extracting(v -> v.getPropertyPath().toString())
                    .containsExactlyInAnyOrder("pageNum", "pageSize");
            }
        }

        @Test
        void filterKeepsRequiredDateValidation() {
            try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
                assertThat(factory.getValidator().validate(new ScmPurchaseReportQueryForm.Filter()))
                    .extracting(v -> v.getPropertyPath().toString())
                    .containsExactlyInAnyOrder("startDate", "endDate");
            }
        }
    }

    /** HTTP非分页入口接收独立的筛选对象；复用相同业务条件，不共享导出时修改的页码。 */
    private static <T> T reportFilter(Object source, Class<T> target) {
        return com.xsy.scm.base.common.util.XsyBeanUtil.copy(source, target);
    }
}
