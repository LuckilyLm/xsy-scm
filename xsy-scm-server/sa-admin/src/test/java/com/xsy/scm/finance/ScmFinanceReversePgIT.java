package com.xsy.scm.finance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.finance.constant.ScmFinancePaymentSourceTypeEnum;
import com.xsy.scm.finance.dao.FinancePaymentDao;
import com.xsy.scm.finance.domain.entity.FinancePaymentEntity;
import com.xsy.scm.finance.domain.form.FinancePaymentAddForm;
import com.xsy.scm.finance.domain.form.FinancePaymentReverseForm;
import com.xsy.scm.finance.domain.form.FinanceReceiptAddForm;
import com.xsy.scm.finance.domain.form.FinanceReceiptReverseForm;
import com.xsy.scm.finance.domain.vo.FinancePaymentVO;
import com.xsy.scm.finance.domain.vo.FinanceReceiptVO;
import com.xsy.scm.finance.service.FinancePaymentService;
import com.xsy.scm.finance.service.FinanceReceiptService;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PostgreSQL coverage for append-only receipt and payment reversals (Finance R1 F1-3C). */
@DisplayName("收付款反向（Finance R1 F1-3C，PG IT）")
class ScmFinanceReversePgIT extends ScmW5PgITBase {

    private static final OffsetDateTime PAST =
            OffsetDateTime.of(2026, 2, 13, 10, 15, 0, 0, ZoneOffset.ofHours(8));

    @Autowired
    private FinanceReceiptService financeReceiptService;

    @Autowired
    private FinancePaymentService financePaymentService;

    @Autowired
    private FinancePaymentDao financePaymentDao;

    @Test
    @DisplayName("收款反向追加事实、记录余额快照，重复同键请求重放首次结果")
    void receiptReverseIsAppendOnlyAndIdempotent() throws Exception {
        Long customerId = newCustomer();
        FinanceReceiptVO original = addReceipt(customerId, "120.0000");
        Map<String, Object> originalBefore = receiptRow(original.getReceiptId());
        FinanceReceiptReverseForm form = receiptReverseForm(original.getReceiptId(), " 误录客户 ");
        String idempotencyKey = key("receipt-reverse");

        FinanceReceiptVO reversal = financeReceiptService.reverse(form, idempotencyKey);
        FinanceReceiptVO replay = financeReceiptService.reverse(form, idempotencyKey);

        assertThat(replay.getReceiptId()).isEqualTo(reversal.getReceiptId());
        assertThat(reversal.getEntryType()).isEqualTo("REVERSE");
        assertThat(reversal.getReverseOfId()).isEqualTo(original.getReceiptId());
        assertThat(reversal.getReason()).isEqualTo("误录客户");
        assertThat(reversal.getAmount()).isEqualByComparingTo("120.0000");

        Map<String, Object> originalAfter = receiptRow(original.getReceiptId());
        assertThat(originalAfter.get("entry_type")).isEqualTo("NORMAL");
        assertThat(originalAfter.get("reverse_of_id")).isNull();
        assertThat(originalAfter.get("reason")).isNull();
        assertThat(originalAfter.get("amount")).isEqualTo(originalBefore.get("amount"));
        assertThat(originalAfter.get("updated_at")).isEqualTo(originalBefore.get("updated_at"));
        assertThat(count("SELECT count(*) FROM finance_receipt WHERE reverse_of_id = ? AND entry_type = 'REVERSE'",
                original.getReceiptId())).isEqualTo(1);

        List<Map<String, Object>> logs = receiptLogsOf(original.getReceiptId());
        assertThat(logs).extracting(row -> row.get("operation_type"))
                .containsExactlyInAnyOrder("RECEIVE", "RECEIPT_REVERSE");
        Map<String, Object> reverseLog = logOfType(logs, "RECEIPT_REVERSE");
        assertThat(reverseLog.get("reason")).isEqualTo("误录客户");
        Map<String, Object> before = jsonObject(reverseLog.get("before_data"));
        Map<String, Object> after = jsonObject(reverseLog.get("after_data"));
        assertThat(before).containsEntry("effectiveAmount", "120.0000");
        assertThat(after).containsEntry("effectiveAmount", "0.0000")
                .containsEntry("reverseOfId", original.getReceiptId().intValue());
    }

    @Test
    @DisplayName("已用核销额非零时拒绝反向，且不新增反向事实或日志")
    void receiptReverseIsBlockedUntilWriteOffIsReversed() {
        Long customerId = newCustomer();
        FinanceReceiptVO original = addReceipt(customerId, "50.0000");
        insertWriteOff("RECEIPT", original.getReceiptId(), "10.0000");

        expectCode(() -> financeReceiptService.reverse(
                receiptReverseForm(original.getReceiptId(), "冲正"), key("blocked-receipt")), 41142);

        assertThat(count("SELECT count(*) FROM finance_receipt WHERE reverse_of_id = ?", original.getReceiptId()))
                .isZero();
        assertThat(receiptLogsOf(original.getReceiptId())).extracting(row -> row.get("operation_type"))
                .containsExactly("RECEIVE");
    }

    @Test
    @DisplayName("不同幂等键重复反向同一收款时由唯一索引语义返回 41143")
    void receiptCannotBeReversedTwice() {
        FinanceReceiptVO original = addReceipt(newCustomer(), "25.0000");
        FinanceReceiptReverseForm form = receiptReverseForm(original.getReceiptId(), "单据重复");

        financeReceiptService.reverse(form, key("first-receipt-reverse"));
        expectCode(() -> financeReceiptService.reverse(form, key("second-receipt-reverse")), 41143);

        assertThat(count("SELECT count(*) FROM finance_receipt WHERE reverse_of_id = ?", original.getReceiptId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("反向原因不能为空白，返回专用业务码 41138")
    void reverseReasonIsRequired() {
        FinanceReceiptVO original = addReceipt(newCustomer(), "25.0000");

        expectCode(() -> financeReceiptService.reverse(receiptReverseForm(original.getReceiptId(), "  "),
                key("missing-reason")), 41138);

        assertThat(count("SELECT count(*) FROM finance_receipt WHERE reverse_of_id = ?", original.getReceiptId()))
                .isZero();
    }

    @Test
    @DisplayName("供应商付款可反向，原付款不变，反向付款保留对方快照")
    void supplierPaymentCanBeReversedWithoutSupplierScope() {
        FinancePaymentVO original = addSupplierPayment(newSupplier("REVERSE-PAY"), "70.0000");
        Map<String, Object> originalBefore = paymentRow(original.getPaymentId());
        FinancePaymentReverseForm form = paymentReverseForm(original.getPaymentId(), "供应商账号录错");

        FinancePaymentVO reversal = financePaymentService.reverse(form, key("supplier-reverse"));

        assertThat(reversal.getEntryType()).isEqualTo("REVERSE");
        assertThat(reversal.getReverseOfId()).isEqualTo(original.getPaymentId());
        assertThat(reversal.getReason()).isEqualTo("供应商账号录错");
        assertThat(reversal.getCounterpartyType()).isEqualTo("SUPPLIER");
        assertThat(reversal.getCounterpartyId()).isEqualTo(original.getCounterpartyId());
        assertThat(reversal.getAmount()).isEqualByComparingTo("70.0000");

        Map<String, Object> originalAfter = paymentRow(original.getPaymentId());
        assertThat(originalAfter.get("entry_type")).isEqualTo("NORMAL");
        assertThat(originalAfter.get("reverse_of_id")).isNull();
        assertThat(originalAfter.get("reason")).isNull();
        assertThat(originalAfter.get("amount")).isEqualTo(originalBefore.get("amount"));
        assertThat(originalAfter.get("updated_at")).isEqualTo(originalBefore.get("updated_at"));
        assertThat(count("SELECT count(*) FROM finance_payment WHERE reverse_of_id = ?", original.getPaymentId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("退款付款反向行清空来源列，不与原退款付款争用来源唯一索引")
    void refundPaymentReverseClearsSource() {
        Long customerId = newCustomer();
        Long originalId = insertRefundPayment(customerId);
        Object sourceId = paymentRow(originalId).get("source_id");

        FinancePaymentVO reversal = financePaymentService.reverse(
                paymentReverseForm(originalId, "退款记录录错"), key("refund-payment-reverse"));

        assertThat(reversal.getEntryType()).isEqualTo("REVERSE");
        assertThat(reversal.getReverseOfId()).isEqualTo(originalId);
        assertThat(reversal.getSourceType()).isNull();
        assertThat(reversal.getSourceId()).isNull();
        Map<String, Object> reversalRow = paymentRow(reversal.getPaymentId());
        assertThat(reversalRow.get("source_type")).isNull();
        assertThat(reversalRow.get("source_id")).isNull();
        assertThat(count("SELECT count(*) FROM finance_payment"
                + " WHERE source_type = 'ORDER_REFUND' AND source_id = ?", sourceId)).isEqualTo(1);
        assertThat(paymentLogsOf(originalId)).extracting(row -> row.get("operation_type"))
                .containsExactly("PAYMENT_REVERSE");
    }

    @Test
    @DisplayName("付款仍有有效核销额时拒绝反向")
    void paymentReverseIsBlockedByWriteOff() {
        FinancePaymentVO original = addSupplierPayment(newSupplier("BLOCK-PAY"), "30.0000");
        insertWriteOff("PAYMENT", original.getPaymentId(), "5.0000");

        expectCode(() -> financePaymentService.reverse(
                paymentReverseForm(original.getPaymentId(), "先撤核销"), key("blocked-payment")), 41142);

        assertThat(count("SELECT count(*) FROM finance_payment WHERE reverse_of_id = ?", original.getPaymentId()))
                .isZero();
        assertThat(paymentLogsOf(original.getPaymentId())).extracting(row -> row.get("operation_type"))
                .containsExactly("PAY");
    }

    @Test
    @DisplayName("客户退款付款反向沿用 customerSellerScope，空范围失败关闭")
    void customerPaymentReverseFailsClosedWithoutSellerScope() {
        Long customerId = newCustomer();
        Long paymentId = insertRefundPayment(customerId);
        setEmployeeWithoutScope();

        assertThatThrownBy(() -> financePaymentService.reverse(
                paymentReverseForm(paymentId, "越权反向"), key("out-of-scope")))
                .isInstanceOf(ScmDataScopeException.class);

        assertThat(count("SELECT count(*) FROM finance_payment WHERE reverse_of_id = ?", paymentId)).isZero();
        assertThat(paymentLogsOf(paymentId)).isEmpty();
    }

    private FinanceReceiptVO addReceipt(Long customerId, String amount) {
        FinanceReceiptAddForm form = new FinanceReceiptAddForm();
        form.setCustomerId(customerId);
        form.setAmount(amount);
        form.setMethod("BANK_TRANSFER");
        form.setReceivedAt(PAST);
        return financeReceiptService.add(form, key("receipt-add"));
    }

    private FinancePaymentVO addSupplierPayment(Long supplierId, String amount) {
        FinancePaymentAddForm form = new FinancePaymentAddForm();
        form.setCounterpartyType("SUPPLIER");
        form.setCounterpartyId(supplierId);
        form.setAmount(amount);
        form.setMethod("BANK_TRANSFER");
        form.setPaidAt(PAST);
        return financePaymentService.add(form, key("payment-add"));
    }

    private Long insertRefundPayment(Long customerId) {
        OffsetDateTime now = OffsetDateTime.now();
        FinancePaymentEntity payment = new FinancePaymentEntity();
        payment.setPaymentNo("PM-" + prefix + "-REFUND");
        payment.setCounterpartyType("CUSTOMER");
        payment.setCounterpartyId(customerId);
        payment.setCounterpartyNameSnapshot("测试客户");
        payment.setAmount(new BigDecimal("32.0000"));
        payment.setMethod("BANK_TRANSFER");
        payment.setPaidAt(PAST);
        payment.setEntryType("NORMAL");
        payment.setSourceType(ScmFinancePaymentSourceTypeEnum.ORDER_REFUND.name());
        payment.setSourceId(700_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 200_000_000L));
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        payment.setCreatedBy(currentOperator());
        payment.setUpdatedBy(currentOperator());
        assertThat(financePaymentDao.insert(payment)).isEqualTo(1);
        return payment.getId();
    }

    private void insertWriteOff(String sourceType, Long sourceId, String amount) {
        jdbc.update("INSERT INTO finance_write_off (write_off_no, source_type, source_id, target_type, target_id,"
                        + " amount, entry_type, written_off_at, operator) VALUES (?, ?, ?, ?, 1, ?, 'NORMAL', now(), ?)",
                "WO-" + prefix + "-" + UUID.randomUUID(), sourceType, sourceId,
                "RECEIPT".equals(sourceType) ? "RECEIVABLE" : "PAYABLE", new BigDecimal(amount), currentOperator());
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

    private FinanceReceiptReverseForm receiptReverseForm(Long receiptId, String reason) {
        FinanceReceiptReverseForm form = new FinanceReceiptReverseForm();
        form.setReceiptId(receiptId);
        form.setReason(reason);
        return form;
    }

    private FinancePaymentReverseForm paymentReverseForm(Long paymentId, String reason) {
        FinancePaymentReverseForm form = new FinancePaymentReverseForm();
        form.setPaymentId(paymentId);
        form.setReason(reason);
        return form;
    }

    private String key(String tag) {
        return prefix + ":" + tag + ":" + UUID.randomUUID();
    }

    private static String currentOperator() {
        return com.xsy.scm.common.constant.ScmOperator.current();
    }

    private Map<String, Object> receiptRow(Long receiptId) {
        return jdbc.queryForMap("SELECT * FROM finance_receipt WHERE id = ?", receiptId);
    }

    private Map<String, Object> paymentRow(Long paymentId) {
        return jdbc.queryForMap("SELECT * FROM finance_payment WHERE id = ?", paymentId);
    }

    private List<Map<String, Object>> receiptLogsOf(Long receiptId) {
        return jdbc.queryForList("SELECT * FROM finance_operation_log"
                + " WHERE business_type = 'RECEIPT' AND business_id = ?", receiptId);
    }

    private List<Map<String, Object>> paymentLogsOf(Long paymentId) {
        return jdbc.queryForList("SELECT * FROM finance_operation_log"
                + " WHERE business_type = 'PAYMENT' AND business_id = ?", paymentId);
    }

    private Map<String, Object> logOfType(List<Map<String, Object>> logs, String operationType) {
        return logs.stream()
                .filter(log -> operationType.equals(log.get("operation_type")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到财务日志类型 " + operationType));
    }

    private Map<String, Object> jsonObject(Object value) throws Exception {
        return objectMapper.readValue(String.valueOf(value), new TypeReference<Map<String, Object>>() {
        });
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
