package com.xsy.scm.finance;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.finance.domain.form.FinancePayableRedForm;
import com.xsy.scm.finance.domain.form.FinancePayableRedItemForm;
import com.xsy.scm.finance.domain.form.FinanceWriteOffAddForm;
import com.xsy.scm.finance.domain.form.FinanceWriteOffAddItemForm;
import com.xsy.scm.finance.domain.form.FinanceWriteOffQueryForm;
import com.xsy.scm.finance.domain.form.FinanceWriteOffReverseForm;
import com.xsy.scm.finance.domain.vo.FinancePayableRedVO;
import com.xsy.scm.finance.domain.vo.FinancePaymentVO;
import com.xsy.scm.finance.domain.vo.FinanceReceiptVO;
import com.xsy.scm.finance.domain.vo.FinanceWriteOffAddResultVO;
import com.xsy.scm.finance.domain.vo.FinanceWriteOffVO;
import com.xsy.scm.finance.service.FinancePayableService;
import com.xsy.scm.finance.service.FinancePaymentService;
import com.xsy.scm.finance.service.FinanceReceiptService;
import com.xsy.scm.finance.service.FinanceWriteOffQueryService;
import com.xsy.scm.finance.service.FinanceWriteOffService;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PostgreSQL coverage for M:N write-off, reverse write-off, and manual RED payable. */
@DisplayName("核销与手工红字应付（Finance R1 F1-4，PG IT）")
class ScmFinanceWriteOffPgIT extends ScmW5PgITBase {

    private static final OffsetDateTime EVENT_AT =
            OffsetDateTime.of(2026, 3, 17, 9, 0, 0, 0, ZoneOffset.ofHours(8));

    @Autowired
    private FinanceReceiptService financeReceiptService;

    @Autowired
    private FinancePaymentService financePaymentService;

    @Autowired
    private FinancePayableService financePayableService;

    @Autowired
    private FinanceWriteOffService financeWriteOffService;

    @Autowired
    private FinanceWriteOffQueryService financeWriteOffQueryService;

    @Test
    @DisplayName("一笔收款分配给多张应收，幂等重放不重复生成事实")
    void oneReceiptCanBeAppliedToManyReceivables() {
        Long customerId = newCustomer();
        FinanceReceiptVO receipt = addReceipt(customerId, "100.0000");
        Long firstReceivableId = normalReceivable(customerId, "80.0000");
        Long secondReceivableId = normalReceivable(customerId, "60.0000");
        FinanceWriteOffAddForm form = writeOffForm("RECEIPT", receipt.getReceiptId(),
                writeOffItem(firstReceivableId, "30.0000"), writeOffItem(secondReceivableId, "40.0000"));
        String key = key("receipt-many-targets");

        FinanceWriteOffAddResultVO result = financeWriteOffService.add(form, key);
        FinanceWriteOffAddResultVO replay = financeWriteOffService.add(form, key);

        assertThat(result.getItems()).hasSize(2);
        assertThat(replay.getItems()).extracting(FinanceWriteOffVO::getWriteOffId)
                .containsExactlyElementsOf(result.getItems().stream().map(FinanceWriteOffVO::getWriteOffId).toList());
        assertThat(count("SELECT count(*) FROM finance_write_off WHERE source_type = 'RECEIPT' AND source_id = ?"
                + " AND entry_type = 'NORMAL'", receipt.getReceiptId())).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM finance_operation_log WHERE business_type = 'WRITE_OFF'"
                + " AND operation_type = 'WRITE_OFF' AND business_id IN"
                + " (SELECT id FROM finance_write_off WHERE source_id = ? AND source_type = 'RECEIPT')",
                receipt.getReceiptId())).isEqualTo(2);
        assertThat(result.getItems()).extracting(FinanceWriteOffVO::getSourceNo)
                .containsOnly(receipt.getReceiptNo());
    }

    @Test
    @DisplayName("供应商付款只能核同一供应商应付")
    void supplierPaymentCanBeAppliedToItsPayables() {
        Long supplierId = newSupplier("WO-PAYMENT");
        FinancePaymentVO payment = addSupplierPayment(supplierId, "90.0000");
        Long payableId = normalPayable(supplierId, "70.0000");

        FinanceWriteOffAddResultVO result = financeWriteOffService.add(
                writeOffForm("PAYMENT", payment.getPaymentId(), writeOffItem(payableId, "50.0000")),
                key("supplier-payment"));

        assertThat(result.getItems()).singleElement().satisfies(row -> {
            assertThat(row.getSourceType()).isEqualTo("PAYMENT");
            assertThat(row.getTargetType()).isEqualTo("PAYABLE");
            assertThat(row.getAmount()).isEqualByComparingTo("50.0000");
            assertThat(row.getEntryType()).isEqualTo("NORMAL");
        });
    }

    @Test
    @DisplayName("核销分页返回 source/target 单号与反向方向，按目标负责人范围过滤")
    void queryReturnsWriteOffRows() {
        Long customerId = newCustomer();
        FinanceReceiptVO receipt = addReceipt(customerId, "50.0000");
        Long targetId = normalReceivable(customerId, "50.0000");
        FinanceWriteOffVO created = financeWriteOffService.add(
                writeOffForm("RECEIPT", receipt.getReceiptId(), writeOffItem(targetId, "10.0000")),
                key("query-add")).getItems().getFirst();
        FinanceWriteOffQueryForm form = writeOffQueryForm(receipt.getReceiptNo());

        var page = financeWriteOffQueryService.query(form);

        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getList()).singleElement().satisfies(row -> {
            assertThat(row.getWriteOffId()).isEqualTo(created.getWriteOffId());
            assertThat(row.getSourceNo()).isEqualTo(receipt.getReceiptNo());
            assertThat(row.getTargetNo()).isNotBlank();
            assertThat(row.getEntryType()).isEqualTo("NORMAL");
        });
    }

    @Test
    @DisplayName("跨客户或跨供应商核销被拒绝并保留 41136")
    void counterpartyMismatchIsRejected() {
        FinanceReceiptVO receipt = addReceipt(newCustomer(), "100.0000");
        Long otherCustomerId = newCustomer();
        Long targetId = normalReceivable(otherCustomerId, "100.0000");

        expectCode(() -> financeWriteOffService.add(
                writeOffForm("RECEIPT", receipt.getReceiptId(), writeOffItem(targetId, "10.0000")),
                key("cross-customer")), 41136);

        assertThat(count("SELECT count(*) FROM finance_write_off WHERE source_type = 'RECEIPT' AND source_id = ?",
                receipt.getReceiptId())).isZero();
    }

    @Test
    @DisplayName("供应商付款不能核到另一家供应商的应付")
    void supplierPaymentCannotBeAppliedAcrossSuppliers() {
        FinancePaymentVO payment = addSupplierPayment(newSupplier("WO-SOURCE-SUPPLIER"), "100.0000");
        Long payableId = normalPayable(newSupplier("WO-TARGET-SUPPLIER"), "100.0000");

        expectCode(() -> financeWriteOffService.add(
                writeOffForm("PAYMENT", payment.getPaymentId(), writeOffItem(payableId, "10.0000")),
                key("cross-supplier")), 41136);

        assertThat(count("SELECT count(*) FROM finance_write_off WHERE source_type = 'PAYMENT' AND source_id = ?",
                payment.getPaymentId())).isZero();
    }

    @Test
    @DisplayName("客户退款付款不能用于供应商应付核销")
    void customerRefundPaymentCannotBeAppliedToSupplierPayable() {
        Long customerId = newCustomer();
        Long paymentId = insertCustomerRefundPayment(customerId);
        Long payableId = normalPayable(newSupplier("WO-CUSTOMER-PAYMENT"), "100.0000");

        expectCode(() -> financeWriteOffService.add(
                writeOffForm("PAYMENT", paymentId, writeOffItem(payableId, "10.0000")),
                key("customer-payment-to-payable")), 41136);

        assertThat(count("SELECT count(*) FROM finance_write_off WHERE source_type = 'PAYMENT' AND source_id = ?",
                paymentId)).isZero();
    }

    @Test
    @DisplayName("单次金额超过资金待核销额或目标 openAmount 时返回 41135")
    void sourceAndTargetLimitsAreBothEnforced() {
        Long customerId = newCustomer();
        FinanceReceiptVO receipt = addReceipt(customerId, "50.0000");
        Long targetId = normalReceivable(customerId, "20.0000");

        expectCode(() -> financeWriteOffService.add(
                writeOffForm("RECEIPT", receipt.getReceiptId(), writeOffItem(targetId, "21.0000")),
                key("target-limit")), 41135);
        expectCode(() -> financeWriteOffService.add(
                writeOffForm("RECEIPT", receipt.getReceiptId(), writeOffItem(targetId, "51.0000")),
                key("source-limit")), 41135);

        assertThat(count("SELECT count(*) FROM finance_write_off WHERE source_type = 'RECEIPT' AND source_id = ?",
                receipt.getReceiptId())).isZero();
    }

    @Test
    @DisplayName("负净应收的 openAmount 为零，不能继续核销")
    void negativeNetReceivableCannotBeWrittenOff() {
        Long customerId = newCustomer();
        FinanceReceiptVO receipt = addReceipt(customerId, "50.0000");
        Long targetId = normalReceivable(customerId, "20.0000");
        insertRedReceivable(targetId, customerId, "30.0000");

        expectCode(() -> financeWriteOffService.add(
                writeOffForm("RECEIPT", receipt.getReceiptId(), writeOffItem(targetId, "1.0000")),
                key("negative-open")), 41135);

        assertThat(count("SELECT count(*) FROM finance_write_off WHERE source_type = 'RECEIPT' AND source_id = ?",
                receipt.getReceiptId())).isZero();
    }

    @Test
    @DisplayName("反向核销追加等额 REVERSE 行，更新派生余额且同一事实只能反向一次")
    void reverseWriteOffAppendsOppositeFact() {
        Long customerId = newCustomer();
        FinanceReceiptVO receipt = addReceipt(customerId, "100.0000");
        Long targetId = normalReceivable(customerId, "80.0000");
        FinanceWriteOffVO normal = financeWriteOffService.add(
                writeOffForm("RECEIPT", receipt.getReceiptId(), writeOffItem(targetId, "40.0000")),
                key("reverse-source-add")).getItems().getFirst();
        Map<String, Object> originalBefore = writeOffRow(normal.getWriteOffId());
        FinanceWriteOffReverseForm form = reverseForm(normal.getWriteOffId(), "重复核销");

        FinanceWriteOffVO reversal = financeWriteOffService.reverse(form, key("reverse-write-off"));
        expectCode(() -> financeWriteOffService.reverse(form, key("reverse-write-off-again")), 41143);

        assertThat(reversal.getEntryType()).isEqualTo("REVERSE");
        assertThat(reversal.getReverseOfId()).isEqualTo(normal.getWriteOffId());
        assertThat(reversal.getAmount()).isEqualByComparingTo("40.0000");
        assertThat(reversal.getReason()).isEqualTo("重复核销");
        Map<String, Object> originalAfter = writeOffRow(normal.getWriteOffId());
        assertThat(originalAfter.get("entry_type")).isEqualTo("NORMAL");
        assertThat(originalAfter.get("reverse_of_id")).isNull();
        assertThat(originalAfter.get("reason")).isNull();
        assertThat(originalAfter.get("amount")).isEqualTo(originalBefore.get("amount"));
        assertThat(count("SELECT count(*) FROM finance_write_off WHERE reverse_of_id = ?",
                normal.getWriteOffId())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM finance_operation_log WHERE business_type = 'WRITE_OFF'"
                + " AND business_id = ? AND operation_type = 'WRITE_OFF_REVERSE'", normal.getWriteOffId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("核销目标的数据范围失败关闭")
    void targetScopeIsEnforced() {
        Long customerId = newCustomer();
        FinanceReceiptVO receipt = addReceipt(customerId, "50.0000");
        Long targetId = normalReceivable(customerId, "50.0000");
        financeWriteOffService.add(
                writeOffForm("RECEIPT", receipt.getReceiptId(), writeOffItem(targetId, "10.0000")),
                key("in-scope-target-write-off"));
        setEmployeeWithoutScope();

        assertThatThrownBy(() -> financeWriteOffService.add(
                writeOffForm("RECEIPT", receipt.getReceiptId(), writeOffItem(targetId, "10.0000")),
                key("target-out-of-scope"))).isInstanceOf(ScmDataScopeException.class);

        assertThat(count("SELECT count(*) FROM finance_write_off WHERE source_id = ? AND source_type = 'RECEIPT'",
                receipt.getReceiptId())).isEqualTo(1);
        FinanceWriteOffQueryForm queryForm = writeOffQueryForm(receipt.getReceiptNo());
        assertThat(financeWriteOffQueryService.query(queryForm).getTotal()).isZero();
    }

    @Test
    @DisplayName("手工红字应付追加 RED 单据和明细，且累计金额不得超过原应付")
    void manualRedPayableIsCappedAndAppendOnly() {
        Long supplierId = newSupplier("WO-RED");
        Long originalId = normalPayable(supplierId, "100.0000");
        Map<String, Object> originalBefore = payableRow(originalId);
        FinancePayableRedForm firstForm = payableRedForm(originalId, "退货差额", redItem("2.0000", "10.0000"));
        String key = key("payable-red");

        FinancePayableRedVO red = financePayableService.red(firstForm, key);
        FinancePayableRedVO replay = financePayableService.red(firstForm, key);
        expectCode(() -> financePayableService.red(
                payableRedForm(originalId, "超额红字", redItem("8.1000", "10.0000")), key("payable-red-over")), 41137);

        assertThat(replay.getPayableId()).isEqualTo(red.getPayableId());
        assertThat(red.getEntryType()).isEqualTo("RED");
        assertThat(red.getOriginalPayableId()).isEqualTo(originalId);
        assertThat(red.getAmount()).isEqualByComparingTo("20.0000");
        assertThat(count("SELECT count(*) FROM finance_payable WHERE original_payable_id = ?"
                + " AND entry_type = 'RED'", originalId)).isEqualTo(1);
        Map<String, Object> originalAfter = payableRow(originalId);
        assertThat(originalAfter.get("entry_type")).isEqualTo("NORMAL");
        assertThat(originalAfter.get("amount")).isEqualTo(originalBefore.get("amount"));
        assertThat(count("SELECT count(*) FROM finance_payable_item WHERE payable_id = ?"
                + " AND source_type = 'MANUAL' AND source_id IS NULL", red.getPayableId())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM finance_operation_log WHERE business_type = 'PAYABLE'"
                + " AND business_id = ? AND operation_type = 'RED_GENERATE'", red.getPayableId())).isEqualTo(1);
    }

    @Test
    @DisplayName("手工红字额度不扣除既有核销额，超额核销继续作为派生事实表达")
    void manualRedCapDoesNotSubtractExistingWriteOffs() {
        Long supplierId = newSupplier("WO-RED-OVER-APPLIED");
        Long originalId = normalPayable(supplierId, "100.0000");
        FinancePaymentVO payment = addSupplierPayment(supplierId, "90.0000");
        financeWriteOffService.add(
                writeOffForm("PAYMENT", payment.getPaymentId(), writeOffItem(originalId, "90.0000")),
                key("write-off-before-red"));

        FinancePayableRedVO red = financePayableService.red(
                payableRedForm(originalId, "退货调整", redItem("2.0000", "10.0000")), key("red-after-write-off"));

        assertThat(red.getAmount()).isEqualByComparingTo("20.0000");
        assertThat(jdbc.queryForObject("SELECT amount FROM finance_payable WHERE original_payable_id = ?"
                + " AND entry_type = 'RED'", BigDecimal.class, originalId)).isEqualByComparingTo("20.0000");
        assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM finance_write_off WHERE target_type = 'PAYABLE'"
                + " AND target_id = ? AND entry_type = 'NORMAL'", BigDecimal.class, originalId))
                .isEqualByComparingTo("90.0000");
    }

    @Test
    @DisplayName("手工红字应付按 purchaserScope 失败关闭")
    void manualRedPayableRequiresPurchaserScope() {
        Long originalId = normalPayable(newSupplier("WO-RED-SCOPE"), "100.0000");
        setEmployeeWithoutScope();

        assertThatThrownBy(() -> financePayableService.red(
                payableRedForm(originalId, "无范围红字", redItem("1.0000", "10.0000")), key("red-out-of-scope")))
                .isInstanceOf(ScmDataScopeException.class);

        assertThat(count("SELECT count(*) FROM finance_payable WHERE original_payable_id = ?"
                + " AND entry_type = 'RED'", originalId)).isZero();
    }

    private FinanceReceiptVO addReceipt(Long customerId, String amount) {
        var form = new com.xsy.scm.finance.domain.form.FinanceReceiptAddForm();
        form.setCustomerId(customerId);
        form.setAmount(amount);
        form.setMethod("BANK_TRANSFER");
        form.setReceivedAt(EVENT_AT);
        return financeReceiptService.add(form, key("receipt-add"));
    }

    private FinancePaymentVO addSupplierPayment(Long supplierId, String amount) {
        var form = new com.xsy.scm.finance.domain.form.FinancePaymentAddForm();
        form.setCounterpartyType("SUPPLIER");
        form.setCounterpartyId(supplierId);
        form.setAmount(amount);
        form.setMethod("BANK_TRANSFER");
        form.setPaidAt(EVENT_AT);
        return financePaymentService.add(form, key("payment-add"));
    }

    private Long normalReceivable(Long customerId, String amount) {
        long sourceId = uniqueNumber();
        return jdbc.queryForObject("INSERT INTO finance_receivable (receivable_no, source_type, source_id, order_id,"
                        + " customer_id, customer_name_snapshot, entry_type, amount, event_at, created_at, updated_at)"
                        + " VALUES (?, 'SALES_ORDER', ?, 1, ?, '核销测试客户', 'NORMAL', ?, now(), now(), now())"
                        + " RETURNING id",
                Long.class, "AR-WO-" + prefix + "-" + UUID.randomUUID(), sourceId, customerId,
                new BigDecimal(amount));
    }

    private void insertRedReceivable(Long originalId, Long customerId, String amount) {
        long sourceId = uniqueNumber();
        jdbc.update("INSERT INTO finance_receivable (receivable_no, source_type, source_id, order_id, customer_id,"
                        + " customer_name_snapshot, entry_type, original_receivable_id, amount, event_at, reason)"
                        + " VALUES (?, 'ORDER_RETURN', ?, 1, ?, '核销测试客户', 'RED', ?, ?, now(), '测试红字')",
                "AR-WO-RED-" + prefix + "-" + UUID.randomUUID(), sourceId, customerId, originalId,
                new BigDecimal(amount));
    }

    private Long normalPayable(Long supplierId, String amount) {
        long receiptId = uniqueNumber();
        Long payableId = jdbc.queryForObject("INSERT INTO finance_payable (payable_no, source_type, source_id,"
                        + " purchase_order_id, supplier_id, supplier_name_snapshot, entry_type, amount, event_at)"
                        + " VALUES (?, 'PURCHASE_RECEIPT', ?, 1, ?, '核销测试供应商', 'NORMAL', ?, now()) RETURNING id",
                Long.class, "AP-WO-" + prefix + "-" + UUID.randomUUID(), receiptId, supplierId,
                new BigDecimal(amount));
        jdbc.update("INSERT INTO finance_payable_item (payable_id, source_type, source_id, purchase_order_item_id,"
                        + " sku_id, sku_name_snapshot, unit_snapshot, quantity, unit_price, amount)"
                        + " VALUES (?, 'PURCHASE_RECEIPT_ITEM', ?, 44, 1, '测试商品', 'kg', 10.0000, 10.0000, 100.0000)",
                payableId, uniqueNumber());
        return payableId;
    }

    private Long insertCustomerRefundPayment(Long customerId) {
        return jdbc.queryForObject("INSERT INTO finance_payment (payment_no, counterparty_type, counterparty_id,"
                        + " counterparty_name_snapshot, amount, method, paid_at, entry_type, source_type, source_id)"
                        + " VALUES (?, 'CUSTOMER', ?, '核销测试客户', 100.0000, 'BANK_TRANSFER', now(),"
                        + " 'NORMAL', 'ORDER_REFUND', ?) RETURNING id",
                Long.class, "PM-WO-" + prefix + "-" + UUID.randomUUID().toString().substring(0, 8),
                customerId, uniqueNumber());
    }

    private FinanceWriteOffAddForm writeOffForm(String sourceType, Long sourceId,
            FinanceWriteOffAddItemForm... items) {
        FinanceWriteOffAddForm form = new FinanceWriteOffAddForm();
        form.setSourceType(sourceType);
        form.setSourceId(sourceId);
        form.setItems(List.of(items));
        return form;
    }

    private FinanceWriteOffAddItemForm writeOffItem(Long targetId, String amount) {
        FinanceWriteOffAddItemForm item = new FinanceWriteOffAddItemForm();
        item.setTargetId(targetId);
        item.setAmount(amount);
        return item;
    }

    private FinanceWriteOffReverseForm reverseForm(Long writeOffId, String reason) {
        FinanceWriteOffReverseForm form = new FinanceWriteOffReverseForm();
        form.setWriteOffId(writeOffId);
        form.setReason(reason);
        return form;
    }

    private FinanceWriteOffQueryForm writeOffQueryForm(String sourceNo) {
        FinanceWriteOffQueryForm form = new FinanceWriteOffQueryForm();
        form.setPageNum(1L);
        form.setPageSize(20L);
        form.setSourceNo(sourceNo);
        form.setStartDate(LocalDate.now(ZoneOffset.ofHours(8)).minusDays(1));
        form.setEndDate(LocalDate.now(ZoneOffset.ofHours(8)));
        return form;
    }

    private FinancePayableRedForm payableRedForm(Long originalPayableId, String reason,
            FinancePayableRedItemForm... items) {
        FinancePayableRedForm form = new FinancePayableRedForm();
        form.setOriginalPayableId(originalPayableId);
        form.setReason(reason);
        form.setItems(List.of(items));
        return form;
    }

    private FinancePayableRedItemForm redItem(String quantity, String unitPrice) {
        FinancePayableRedItemForm item = new FinancePayableRedItemForm();
        item.setPurchaseOrderItemId(44L);
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice);
        item.setAmount(new BigDecimal(quantity).multiply(new BigDecimal(unitPrice))
                .setScale(4, java.math.RoundingMode.HALF_UP).toPlainString());
        return item;
    }

    private void setEmployeeWithoutScope() {
        String loginName = (prefix + "-NO-SCOPE").toUpperCase();
        jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                        + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                UUID.randomUUID().toString().replace("-", ""), loginName,
                "$argon2id$it-placeholder", "无范围财务员工");
        Long employeeId = jdbc.queryForObject(
                "SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, loginName);
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(employeeId);
        employee.setActualName("无范围财务员工");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(false);
        SmartRequestUtil.setRequestUser(employee);
        evictMybatisCache();
    }

    private Map<String, Object> writeOffRow(Long id) {
        return jdbc.queryForMap("SELECT * FROM finance_write_off WHERE id = ?", id);
    }

    private Map<String, Object> payableRow(Long id) {
        return jdbc.queryForMap("SELECT * FROM finance_payable WHERE id = ?", id);
    }

    private String key(String tag) {
        return prefix + ":" + tag + ":" + UUID.randomUUID();
    }

    private long uniqueNumber() {
        return 1_000_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 8_000_000_000L);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
