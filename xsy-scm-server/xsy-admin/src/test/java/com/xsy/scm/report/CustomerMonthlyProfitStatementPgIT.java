package com.xsy.scm.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

import cn.dev33.satoken.stp.StpUtil;
import com.xsy.scm.common.ScmW6PgITBase;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.finance.permission.FinancePermission;
import com.xsy.scm.finance.domain.form.FinanceCustomerExpenseForm;
import com.xsy.scm.finance.service.FinanceCustomerExpenseService;
import com.xsy.scm.report.constant.ScmFinanceProfitDimensionEnum;
import com.xsy.scm.report.domain.form.ScmCustomerMonthlyProfitQueryForm;
import com.xsy.scm.report.service.ScmCustomerMonthlyProfitService;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 客户月利润的对账规则：应收净额、收款净额、有效过账和暂定费用公式。
 *
 * <p>这些列全部是报表派生值，不写入数据库；用例直接写应收 / 收款 / 过账记录，避免把报表公式
 * 和订单、退货链路绑在一起。
 */
class CustomerMonthlyProfitStatementPgIT extends ScmW6PgITBase {
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
