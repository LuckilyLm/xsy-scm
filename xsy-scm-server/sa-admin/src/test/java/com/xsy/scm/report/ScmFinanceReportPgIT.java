package com.xsy.scm.report;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderVO;
import com.xsy.scm.purchase.domain.vo.PurchaseReceiptVO;
import com.xsy.scm.report.dao.ScmFinanceReportDao;
import com.xsy.scm.report.domain.form.ScmFinanceOverviewQueryForm;
import com.xsy.scm.report.domain.form.ScmFinanceReportQueryForm;
import com.xsy.scm.report.domain.vo.ScmFinanceOverviewVO;
import com.xsy.scm.report.domain.vo.ScmFinancePayableDetailVO;
import com.xsy.scm.report.domain.vo.ScmFinanceReceivableDetailVO;
import com.xsy.scm.report.support.ScmReportTimeRange;
import com.xsy.scm.report.support.ScmReportTimeRangeResolver;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Finance R0 flow / stock separation and per-document balance formulas. */
@DisplayName("Finance R0 往来概览（PG IT）")
class ScmFinanceReportPgIT extends ScmW5PgITBase {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private ScmFinanceReportDao financeReportDao;

    @Test
    @DisplayName("本期发生额按事件日期；期末待收 / 待付包含期初并逐单截零")
    void flowsUseTheWindowButEndingBalancesIncludeOpeningDocuments() {
        Long customerId = newCustomer();
        Long scopeOwnerId = jdbc.queryForObject(
                "SELECT COALESCE(MAX(employee_id), 0) + 100000 FROM t_employee", Long.class);
        jdbc.update("UPDATE customer SET seller_id = ? WHERE id = ?", scopeOwnerId, customerId);
        evictMybatisCache();
        Long saleSkuId = newOnShelfSku("RPT-AR");
        Long oldOrderId = confirmedSalesOrder(customerId, saleSkuId, "10.0000", "10.0000");
        Long currentOrderId = confirmedSalesOrder(customerId, saleSkuId, "8.0000", "8.0000");
        Long overAppliedOrderId = confirmedSalesOrder(customerId, saleSkuId, "1.0000", "1.0000");
        String customerName = jdbc.queryForObject("SELECT name FROM customer WHERE id = ?", String.class, customerId);

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
        String supplierName = jdbc.queryForObject("SELECT name FROM supplier WHERE id = ?", String.class, supplierId);
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
        return jdbc.queryForObject("INSERT INTO finance_receivable "
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
        jdbc.update("UPDATE purchase_order SET purchaser_id = ? WHERE id = ?", scopeOwnerId, order.getId());
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
        return jdbc.queryForObject("INSERT INTO finance_payable "
                        + "(payable_no, source_type, source_id, purchase_order_id, supplier_id, "
                        + "supplier_name_snapshot, entry_type, original_payable_id, amount, event_at, reason) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, prefix + "-" + suffix, resolvedSourceType, sourceId, fact.purchaseOrderId(), supplierId,
                supplierName, entryType, originalId, new BigDecimal(amount), eventAt, reason);
    }

    private Long receipt(Long customerId, String customerName, String amount, OffsetDateTime receivedAt) {
        return jdbc.queryForObject("INSERT INTO finance_receipt "
                        + "(receipt_no, customer_id, customer_name_snapshot, settlement_customer_id, "
                        + "settlement_customer_name_snapshot, amount, method, received_at, entry_type) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'BANK_TRANSFER', ?, 'NORMAL') RETURNING id",
                Long.class, prefix + "-RC-" + UUID.randomUUID(), customerId, customerName, customerId, customerName,
                new BigDecimal(amount), receivedAt);
    }

    private Long payment(Long supplierId, String supplierName, String amount, OffsetDateTime paidAt) {
        return jdbc.queryForObject("INSERT INTO finance_payment "
                        + "(payment_no, counterparty_type, counterparty_id, counterparty_name_snapshot, amount, "
                        + "method, paid_at, entry_type) "
                        + "VALUES (?, 'SUPPLIER', ?, ?, ?, 'BANK_TRANSFER', ?, 'NORMAL') RETURNING id",
                Long.class, prefix + "-PM-" + UUID.randomUUID(), supplierId, supplierName,
                new BigDecimal(amount), paidAt);
    }

    private Long writeOff(String suffix, String sourceType, Long sourceId, String targetType, Long targetId,
            String amount, String entryType, Long reverseOfId, OffsetDateTime writtenOffAt, String reason) {
        return jdbc.queryForObject("INSERT INTO finance_write_off "
                        + "(write_off_no, source_type, source_id, target_type, target_id, amount, entry_type, "
                        + "reverse_of_id, reason, written_off_at, operator) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, prefix + "-" + suffix, sourceType, sourceId, targetType, targetId,
                new BigDecimal(amount), entryType, reverseOfId, reason, writtenOffAt, "Finance R0 IT");
    }

    private record PurchaseFact(Long purchaseOrderId, Long receiptId) {
    }
}
