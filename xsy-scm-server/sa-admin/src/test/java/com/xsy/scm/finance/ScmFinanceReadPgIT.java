package com.xsy.scm.finance;

import cn.dev33.satoken.stp.StpUtil;
import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.finance.domain.form.FinanceOperationLogQueryForm;
import com.xsy.scm.finance.domain.form.FinanceDatePageForm;
import com.xsy.scm.finance.domain.form.FinancePayableQueryForm;
import com.xsy.scm.finance.domain.form.FinancePayableRedForm;
import com.xsy.scm.finance.domain.form.FinancePayableRedItemForm;
import com.xsy.scm.finance.domain.form.FinancePaymentAddForm;
import com.xsy.scm.finance.domain.form.FinancePaymentQueryForm;
import com.xsy.scm.finance.domain.form.FinanceRefundOptionQueryForm;
import com.xsy.scm.finance.domain.form.FinanceReceivableQueryForm;
import com.xsy.scm.finance.domain.form.FinanceReceiptAddForm;
import com.xsy.scm.finance.domain.form.FinanceReceiptQueryForm;
import com.xsy.scm.finance.domain.form.FinanceWriteOffAddForm;
import com.xsy.scm.finance.domain.form.FinanceWriteOffAddItemForm;
import com.xsy.scm.finance.domain.form.FinanceWriteOffQueryForm;
import com.xsy.scm.finance.permission.FinancePermission;
import com.xsy.scm.finance.service.FinanceOperationLogQueryService;
import com.xsy.scm.finance.service.FinancePayableQueryService;
import com.xsy.scm.finance.service.FinancePayableService;
import com.xsy.scm.finance.service.FinancePaymentQueryService;
import com.xsy.scm.finance.service.FinancePaymentService;
import com.xsy.scm.finance.service.FinanceRefundOptionQueryService;
import com.xsy.scm.finance.service.FinanceReceivableQueryService;
import com.xsy.scm.finance.service.FinanceReceiptQueryService;
import com.xsy.scm.finance.service.FinanceReceiptService;
import com.xsy.scm.finance.service.FinanceWriteOffQueryService;
import com.xsy.scm.finance.service.FinanceWriteOffService;
import com.xsy.scm.finance.controller.FinanceReadController;
import com.xsy.scm.finance.controller.FinanceWriteOffController;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Finance R1 只读查询与 Excel 导出（PG IT）")
class ScmFinanceReadPgIT extends ScmW5PgITBase {

    private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.ofHours(8));

    @Autowired
    private FinanceReceivableQueryService receivableQueries;

    @Autowired
    private FinancePayableQueryService payableQueries;

    @Autowired
    private FinanceReceiptQueryService receiptQueries;

    @Autowired
    private FinancePaymentQueryService paymentQueries;

    @Autowired
    private FinanceRefundOptionQueryService refundOptionQueries;

    @Autowired
    private FinanceWriteOffQueryService writeOffQueries;

    @Autowired
    private FinanceReceiptService receiptService;

    @Autowired
    private FinancePaymentService paymentService;

    @Autowired
    private FinancePayableService payableService;

    @Autowired
    private FinanceWriteOffService writeOffService;

    @Autowired
    private FinanceOperationLogQueryService operationLogQueries;

    @Autowired
    private FinanceReadController readController;

    @Autowired
    private FinanceWriteOffController writeOffController;

    @Test
    @DisplayName("应收查询派生余额、详情来源链和导出共用同一筛选")
    void receivableQueryDetailAndExport() throws Exception {
        Long customerId = newCustomer();
        Long normalId = normalReceivable(customerId, "100.0000");
        FinanceReceiptAddForm receiptForm = new FinanceReceiptAddForm();
        receiptForm.setCustomerId(customerId);
        receiptForm.setAmount("90.0000");
        receiptForm.setMethod("CASH");
        receiptForm.setReceivedAt(NOW);
        var receipt = receiptService.add(receiptForm, key("ar-receipt"));
        writeOffService.add(writeOffForm("RECEIPT", receipt.getReceiptId(), normalId, "90.0000"), key("ar-apply"));
        insertRedReceivable(normalId, customerId, "20.0000");

        FinanceReceivableQueryForm form = dateRange(new FinanceReceivableQueryForm());
        form.setReceivableId(normalId);
        form.setSettleState("PARTIAL");
        var page = receivableQueries.query(form);

        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getList().getFirst().getNetAmount()).isEqualByComparingTo("80.0000");
        assertThat(page.getList().getFirst().getOpenAmount()).isEqualByComparingTo("0.0000");
        assertThat(page.getList().getFirst().getOverAppliedAmount()).isEqualByComparingTo("10.0000");
        var detail = receivableQueries.detail(normalId);
        assertThat(detail.getRedEntries()).hasSize(1);
        assertThat(detail.getItems()).hasSize(1);
        assertThat(detail.getWriteOffs()).hasSize(1);
        assertWorkbook("应收单号", page.getList().getFirst().getReceivableNo(),
                response -> readController.exportReceivables(form, response), 12);
    }

    @Test
    @DisplayName("应付查询按收货负责人范围派生余额并导出红字事实")
    void payableQueryDetailAndExport() throws Exception {
        Long supplierId = newSupplier("READ-AP");
        Long normalId = normalPayable(supplierId, "100.0000");
        FinancePaymentAddForm paymentForm = supplierPayment(supplierId, "90.0000");
        var payment = paymentService.add(paymentForm, key("read-ap-payment"));
        writeOffService.add(writeOffForm("PAYMENT", payment.getPaymentId(), normalId, "90.0000"), key("read-ap-apply"));
        FinancePayableRedForm redForm = payableRedForm(normalId, redItem("2.0000", "10.0000"));
        payableService.red(redForm, key("read-ap-red"));

        FinancePayableQueryForm form = dateRange(new FinancePayableQueryForm());
        form.setPayableId(normalId);
        form.setSupplierName("只读测试供应商");
        form.setSettleState("PARTIAL");
        var page = payableQueries.query(form);

        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getList().getFirst().getNetAmount()).isEqualByComparingTo("80.0000");
        assertThat(page.getList().getFirst().getOverAppliedAmount()).isEqualByComparingTo("10.0000");
        var detail = payableQueries.detail(normalId);
        assertThat(detail.getRedEntries()).hasSize(1);
        assertThat(detail.getItems()).hasSize(1);
        assertThat(detail.getWriteOffs()).hasSize(1);
        assertWorkbook("应付单号", page.getList().getFirst().getPayableNo(),
                response -> readController.exportPayables(form, response), 12);
    }

    @Test
    @DisplayName("收款查询和导出显示有效额、已用额与待核销额")
    void receiptQueryDetailAndExport() throws Exception {
        Long customerId = newCustomer();
        FinanceReceiptAddForm addForm = new FinanceReceiptAddForm();
        addForm.setCustomerId(customerId);
        addForm.setAmount("100.0000");
        addForm.setMethod("BANK_TRANSFER");
        addForm.setReceivedAt(NOW);
        var receipt = receiptService.add(addForm, key("read-receipt"));
        Long targetId = normalReceivable(customerId, "100.0000");
        writeOffService.add(writeOffForm("RECEIPT", receipt.getReceiptId(), targetId, "40.0000"), key("read-receipt-apply"));

        FinanceReceiptQueryForm form = dateRange(new FinanceReceiptQueryForm());
        form.setReceiptId(receipt.getReceiptId());
        var page = receiptQueries.query(form);

        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getList().getFirst().getEffectiveAmount()).isEqualByComparingTo("100.0000");
        assertThat(page.getList().getFirst().getUsedAmount()).isEqualByComparingTo("40.0000");
        assertThat(page.getList().getFirst().getPendingWriteOffAmount()).isEqualByComparingTo("60.0000");
        assertThat(receiptQueries.detail(receipt.getReceiptId()).getWriteOffs()).hasSize(1);
        assertWorkbook("收款单号", page.getList().getFirst().getReceiptNo(),
                response -> readController.exportReceipts(form, response), 11);

        FinanceOperationLogQueryForm logForm = new FinanceOperationLogQueryForm();
        logForm.setBusinessType("RECEIPT");
        logForm.setBusinessId(receipt.getReceiptId());
        try (MockedStatic<StpUtil> stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.checkPermission(FinancePermission.RECEIPT_QUERY)).thenAnswer(invocation -> null);
            assertThat(operationLogQueries.query(logForm)).extracting(row -> row.getOperationType())
                    .containsExactly("RECEIVE");
        }
    }

    @Test
    @DisplayName("付款查询沿用供应商共享范围，导出退款来源快照")
    void paymentQueryDetailAndExport() throws Exception {
        Long supplierId = newSupplier("READ-PM");
        FinancePaymentAddForm addForm = supplierPayment(supplierId, "80.0000");
        var payment = paymentService.add(addForm, key("read-payment"));
        Long payableId = normalPayable(supplierId, "80.0000");
        writeOffService.add(writeOffForm("PAYMENT", payment.getPaymentId(), payableId, "30.0000"), key("read-payment-apply"));

        FinancePaymentQueryForm form = dateRange(new FinancePaymentQueryForm());
        form.setPaymentId(payment.getPaymentId());
        form.setCounterpartyName(jdbc.queryForObject("SELECT counterparty_name_snapshot FROM finance_payment WHERE id = ?",
                String.class, payment.getPaymentId()));
        var page = paymentQueries.query(form);

        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getList().getFirst().getEffectiveAmount()).isEqualByComparingTo("80.0000");
        assertThat(page.getList().getFirst().getUsedAmount()).isEqualByComparingTo("30.0000");
        assertThat(paymentQueries.detail(payment.getPaymentId()).getWriteOffs()).hasSize(1);
        assertWorkbook("付款单号", page.getList().getFirst().getPaymentNo(),
                response -> readController.exportPayments(form, response), 12);
    }

    @Test
    @DisplayName("客户付款退款选择器只返回范围内已完成且尚未付款的退款")
    void refundOptionPickerReturnsOnlyEligibleRefunds() {
        Long customerId = newCustomer();
        String refundNo = "RF-READ-" + prefix + "-" + UUID.randomUUID();
        Long refundId = jdbc.queryForObject(
                "INSERT INTO order_refund (refund_no, return_id, order_id, customer_id, refund_amount, status, completed_at)"
                        + " VALUES (?, ?, ?, ?, 25.5000, 'COMPLETED', ?) RETURNING id",
                Long.class, refundNo, uniqueNumber(), uniqueNumber(), customerId, NOW);

        FinanceRefundOptionQueryForm form = new FinanceRefundOptionQueryForm();
        form.setKeyword(refundNo);
        var options = refundOptionQueries.query(form);
        assertThat(options.getTotal()).isEqualTo(1L);
        var option = options.getList().getFirst();
        assertThat(option.getRefundId()).isEqualTo(refundId);
        assertThat(option.getRefundNo()).isEqualTo(refundNo);
        assertThat(option.getCustomerId()).isEqualTo(customerId);
        assertThat(option.getRefundAmount()).isEqualByComparingTo("25.5000");

        FinancePaymentAddForm payment = new FinancePaymentAddForm();
        payment.setCounterpartyType("CUSTOMER");
        payment.setCounterpartyId(customerId);
        payment.setAmount("25.5000");
        payment.setMethod("BANK_TRANSFER");
        payment.setPaidAt(NOW);
        payment.setSourceType("ORDER_REFUND");
        payment.setSourceId(refundId);
        paymentService.add(payment, key("refund-option-paid"));

        var afterPayment = refundOptionQueries.query(form);
        assertThat(afterPayment.getTotal()).isZero();
        assertThat(afterPayment.getList()).isEmpty();
    }

    @Test
    @DisplayName("核销导出复用目标范围与列表筛选")
    void writeOffQueryAndExport() throws Exception {
        Long customerId = newCustomer();
        FinanceReceiptAddForm receiptForm = new FinanceReceiptAddForm();
        receiptForm.setCustomerId(customerId);
        receiptForm.setAmount("30.0000");
        receiptForm.setMethod("CASH");
        receiptForm.setReceivedAt(NOW);
        var receipt = receiptService.add(receiptForm, key("read-writeoff-receipt"));
        Long targetId = normalReceivable(customerId, "30.0000");
        var writeOff = writeOffService.add(writeOffForm("RECEIPT", receipt.getReceiptId(), targetId, "10.0000"),
                key("read-writeoff-add")).getItems().getFirst();
        FinanceWriteOffQueryForm form = dateRange(new FinanceWriteOffQueryForm());
        form.setWriteOffId(writeOff.getWriteOffId());

        assertThat(writeOffQueries.query(form).getList()).singleElement()
                .satisfies(row -> assertThat(row.getWriteOffId()).isEqualTo(writeOff.getWriteOffId()));
        assertWorkbook("核销单号", writeOff.getWriteOffNo(), response -> writeOffController.export(form, response), 12);
    }

    private void assertWorkbook(String firstHeader, String firstData, WorkbookCall export, int columnCount)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        export.run(response);
        byte[] bytes = response.getContentAsByteArray();
        assertThat(bytes.length).isGreaterThan(500);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getPhysicalNumberOfRows()).isGreaterThanOrEqualTo(2);
            assertThat(sheet.getRow(0).getPhysicalNumberOfCells()).isEqualTo(columnCount);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo(firstHeader);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo(firstData);
        }
    }

    private <T extends FinanceDatePageForm> T dateRange(T form) {
        LocalDate today = LocalDate.now(ZoneOffset.ofHours(8));
        form.setStartDate(today.minusDays(1));
        form.setEndDate(today.plusDays(1));
        return form;
    }

    private Long normalReceivable(Long customerId, String amount) {
        Long skuId = newOnShelfSku("READ-AR");
        Long orderId = confirmedSalesOrder(customerId, skuId, "1.0000", "1.0000");
        Long receivableId = jdbc.queryForObject("INSERT INTO finance_receivable (receivable_no, source_type, source_id,"
                        + " order_id, customer_id, customer_name_snapshot, settlement_customer_id,"
                        + " settlement_customer_name_snapshot, entry_type, amount, event_at)"
                        + " VALUES (?, 'SALES_ORDER', ?, ?, ?, '只读测试客户', ?, '只读测试客户', 'NORMAL', ?, now()) RETURNING id",
                Long.class, "AR-READ-" + prefix + "-" + UUID.randomUUID(), orderId, orderId, customerId, customerId,
                new BigDecimal(amount));
        jdbc.update("INSERT INTO finance_receivable_item (receivable_id, source_type, source_id, order_item_id,"
                        + " sku_id, sku_name_snapshot, unit_snapshot, quantity, unit_price, amount)"
                        + " VALUES (?, 'INVENTORY_OUTBOUND_ITEM', ?, 1, 1, '只读测试商品', 'kg', 1.0000, ?, ?)",
                receivableId, uniqueNumber(), new BigDecimal(amount), new BigDecimal(amount));
        return receivableId;
    }

    private void insertRedReceivable(Long originalId, Long customerId, String amount) {
        jdbc.update("INSERT INTO finance_receivable (receivable_no, source_type, source_id, order_id, customer_id,"
                        + " customer_name_snapshot, settlement_customer_id, settlement_customer_name_snapshot,"
                        + " entry_type, original_receivable_id, amount, event_at, reason)"
                        + " VALUES (?, 'ORDER_RETURN', ?, ?, ?, '只读测试客户', ?, '只读测试客户', 'RED', ?, ?, now(), '只读测试红字')",
                "AR-READ-RED-" + prefix + "-" + UUID.randomUUID(), uniqueNumber(),
                jdbc.queryForObject("SELECT order_id FROM finance_receivable WHERE id = ?", Long.class, originalId),
                customerId, customerId, originalId, new BigDecimal(amount));
    }

    private Long normalPayable(Long supplierId, String amount) {
        long receiptId = uniqueNumber();
        Long payableId = jdbc.queryForObject("INSERT INTO finance_payable (payable_no, source_type, source_id,"
                        + " purchase_order_id, supplier_id, supplier_name_snapshot, entry_type, amount, event_at)"
                        + " VALUES (?, 'PURCHASE_RECEIPT', ?, 1, ?, '只读测试供应商', 'NORMAL', ?, now()) RETURNING id",
                Long.class, "AP-READ-" + prefix + "-" + UUID.randomUUID(), receiptId, supplierId,
                new BigDecimal(amount));
        jdbc.update("INSERT INTO finance_payable_item (payable_id, source_type, source_id, purchase_order_item_id,"
                        + " sku_id, sku_name_snapshot, unit_snapshot, quantity, unit_price, amount)"
                        + " VALUES (?, 'PURCHASE_RECEIPT_ITEM', ?, 44, 1, '只读测试商品', 'kg', 1.0000, ?, ?)",
                payableId, uniqueNumber(), new BigDecimal(amount), new BigDecimal(amount));
        return payableId;
    }

    private FinancePayableRedForm payableRedForm(Long originalId, FinancePayableRedItemForm item) {
        FinancePayableRedForm form = new FinancePayableRedForm();
        form.setOriginalPayableId(originalId);
        form.setReason("只读测试红字");
        form.setItems(List.of(item));
        return form;
    }

    private FinancePayableRedItemForm redItem(String quantity, String price) {
        FinancePayableRedItemForm form = new FinancePayableRedItemForm();
        form.setPurchaseOrderItemId(44L);
        form.setQuantity(quantity);
        form.setUnitPrice(price);
        form.setAmount(new BigDecimal(quantity).multiply(new BigDecimal(price))
                .setScale(4, java.math.RoundingMode.HALF_UP).toPlainString());
        return form;
    }

    private FinanceWriteOffAddForm writeOffForm(String sourceType, Long sourceId, Long targetId, String amount) {
        FinanceWriteOffAddItemForm item = new FinanceWriteOffAddItemForm();
        item.setTargetId(targetId);
        item.setAmount(amount);
        FinanceWriteOffAddForm form = new FinanceWriteOffAddForm();
        form.setSourceType(sourceType);
        form.setSourceId(sourceId);
        form.setItems(List.of(item));
        return form;
    }

    private FinanceReceiptAddForm receiptForm(Long customerId, String amount) {
        FinanceReceiptAddForm form = new FinanceReceiptAddForm();
        form.setCustomerId(customerId);
        form.setAmount(amount);
        form.setMethod("BANK_TRANSFER");
        form.setReceivedAt(NOW);
        return form;
    }

    private FinancePaymentAddForm supplierPayment(Long supplierId, String amount) {
        FinancePaymentAddForm form = new FinancePaymentAddForm();
        form.setCounterpartyType("SUPPLIER");
        form.setCounterpartyId(supplierId);
        form.setAmount(amount);
        form.setMethod("BANK_TRANSFER");
        form.setPaidAt(NOW);
        return form;
    }

    private long uniqueNumber() {
        return 1_000_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 8_000_000_000L);
    }

    private String key(String tag) {
        return prefix + ":" + tag + ":" + UUID.randomUUID();
    }

    @FunctionalInterface
    private interface WorkbookCall {
        void run(MockHttpServletResponse response) throws Exception;
    }
}
