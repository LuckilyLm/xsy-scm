package com.xsy.scm.finance;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.finance.domain.form.FinanceReceiptAddForm;
import com.xsy.scm.finance.domain.form.FinanceReceiptReverseForm;
import com.xsy.scm.finance.domain.form.FinanceWriteOffAddForm;
import com.xsy.scm.finance.domain.form.FinanceWriteOffAddItemForm;
import com.xsy.scm.finance.domain.form.FinanceWriteOffReverseForm;
import com.xsy.scm.finance.domain.vo.FinanceReceiptVO;
import com.xsy.scm.finance.domain.vo.FinanceWriteOffVO;
import com.xsy.scm.finance.service.FinanceReceiptService;
import com.xsy.scm.finance.service.FinanceWriteOffService;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Real PostgreSQL races for finance source and target row locks. */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("Finance R1 并发锁序（PG IT，无外层事务）")
class ScmFinanceConcurrencyPgIT extends ScmW5PgITBase {

    private static final OffsetDateTime EVENT_AT =
            OffsetDateTime.of(2026, 5, 20, 14, 0, 0, 0, ZoneOffset.ofHours(8));

    @Autowired
    private FinanceReceiptService financeReceiptService;

    @Autowired
    private FinanceWriteOffService financeWriteOffService;

    @Override
    protected void evictMybatisCache() {
        // No outer transaction means JdbcTemplate and each command use independent sessions.
    }

    @Test
    @DisplayName("同一收款并发反向：恰好一条 REVERSE，失败侧为 41143")
    void concurrentReceiptReversalsConvergeToOneFact() throws Exception {
        FinanceReceiptVO receipt = receipt("100.0000");
        var firstFailure = new AtomicReference<Throwable>();
        var secondFailure = new AtomicReference<Throwable>();
        var firstResult = new AtomicReference<FinanceReceiptVO>();
        var secondResult = new AtomicReference<FinanceReceiptVO>();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(2);

        run("receipt-reverse-a", () -> firstResult.set(
                financeReceiptService.reverse(receiptReverse(receipt.getReceiptId()), key("receipt-race-a"))),
                firstFailure, ready, start, done);
        run("receipt-reverse-b", () -> secondResult.set(
                financeReceiptService.reverse(receiptReverse(receipt.getReceiptId()), key("receipt-race-b"))),
                secondFailure, ready, start, done);
        start(ready, start, done);

        assertThat((firstResult.get() != null) ^ (secondResult.get() != null))
                .as("exactly one reversal command succeeds")
                .isTrue();
        Throwable loser = firstFailure.get() != null ? firstFailure.get() : secondFailure.get();
        assertBusinessCode(loser, 41143);
        assertThat(count("SELECT count(*) FROM finance_receipt WHERE reverse_of_id = ? AND entry_type = 'REVERSE'",
                receipt.getReceiptId())).isEqualTo(1);
    }

    @Test
    @DisplayName("两笔并发核销竞争同一余额：一笔成功，另一笔以 41135 失败")
    void concurrentWriteOffsCannotOverspendTarget() throws Exception {
        Long customerId = newCustomer();
        FinanceReceiptVO receipt = receipt(customerId, "100.0000");
        Long targetId = normalReceivable(customerId, "100.0000");
        var firstFailure = new AtomicReference<Throwable>();
        var secondFailure = new AtomicReference<Throwable>();
        var firstResult = new AtomicReference<Object>();
        var secondResult = new AtomicReference<Object>();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(2);

        run("write-off-a", () -> firstResult.set(financeWriteOffService.add(
                writeOff(receipt.getReceiptId(), targetId, "70.0000"), key("write-off-race-a"))),
                firstFailure, ready, start, done);
        run("write-off-b", () -> secondResult.set(financeWriteOffService.add(
                writeOff(receipt.getReceiptId(), targetId, "70.0000"), key("write-off-race-b"))),
                secondFailure, ready, start, done);
        start(ready, start, done);

        assertThat((firstResult.get() != null) ^ (secondResult.get() != null))
                .as("one allocation commits and one sees the remaining open amount")
                .isTrue();
        Throwable loser = firstFailure.get() != null ? firstFailure.get() : secondFailure.get();
        assertBusinessCode(loser, 41135);
        assertThat(count("SELECT count(*) FROM finance_write_off"
                + " WHERE source_type = 'RECEIPT' AND source_id = ? AND target_type = 'RECEIVABLE' AND target_id = ?"
                + " AND entry_type = 'NORMAL'", receipt.getReceiptId(), targetId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM finance_write_off"
                + " WHERE target_type = 'RECEIVABLE' AND target_id = ? AND deleted = FALSE",
                BigDecimal.class, targetId)).isEqualByComparingTo("70.0000");
    }

    @Test
    @DisplayName("同一核销并发反向：唯一反向行获胜，另一笔得到 41143")
    void concurrentWriteOffReversalsConvergeToOneFact() throws Exception {
        Long customerId = newCustomer();
        FinanceReceiptVO receipt = receipt(customerId, "100.0000");
        Long targetId = normalReceivable(customerId, "100.0000");
        FinanceWriteOffVO normal = financeWriteOffService.add(
                writeOff(receipt.getReceiptId(), targetId, "40.0000"), key("write-off-before-race"))
                .getItems().getFirst();
        var firstFailure = new AtomicReference<Throwable>();
        var secondFailure = new AtomicReference<Throwable>();
        var firstResult = new AtomicReference<FinanceWriteOffVO>();
        var secondResult = new AtomicReference<FinanceWriteOffVO>();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(2);

        run("write-off-reverse-a", () -> firstResult.set(financeWriteOffService.reverse(
                writeOffReverse(normal.getWriteOffId()), key("write-off-reverse-race-a"))),
                firstFailure, ready, start, done);
        run("write-off-reverse-b", () -> secondResult.set(financeWriteOffService.reverse(
                writeOffReverse(normal.getWriteOffId()), key("write-off-reverse-race-b"))),
                secondFailure, ready, start, done);
        start(ready, start, done);

        assertThat((firstResult.get() != null) ^ (secondResult.get() != null))
                .as("one reversal wins the source/target/original lock sequence")
                .isTrue();
        Throwable loser = firstFailure.get() != null ? firstFailure.get() : secondFailure.get();
        assertBusinessCode(loser, 41143);
        assertThat(count("SELECT count(*) FROM finance_write_off WHERE reverse_of_id = ?"
                + " AND entry_type = 'REVERSE'", normal.getWriteOffId())).isEqualTo(1);
    }

    @Test
    @DisplayName("收款反向与核销反向共用来源锁，不会产生负待核销额")
    void receiptAndWriteOffReversalShareSourceLock() throws Exception {
        Long customerId = newCustomer();
        FinanceReceiptVO receipt = receipt(customerId, "100.0000");
        Long targetId = normalReceivable(customerId, "100.0000");
        FinanceWriteOffVO normal = financeWriteOffService.add(
                writeOff(receipt.getReceiptId(), targetId, "50.0000"), key("shared-lock-write-off"))
                .getItems().getFirst();
        var writeOffFailure = new AtomicReference<Throwable>();
        var receiptFailure = new AtomicReference<Throwable>();
        var writeOffResult = new AtomicReference<FinanceWriteOffVO>();
        var receiptResult = new AtomicReference<FinanceReceiptVO>();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(2);

        run("write-off-reverse-shared", () -> writeOffResult.set(financeWriteOffService.reverse(
                writeOffReverse(normal.getWriteOffId()), key("shared-lock-write-off-reverse"))),
                writeOffFailure, ready, start, done);
        run("receipt-reverse-shared", () -> receiptResult.set(financeReceiptService.reverse(
                receiptReverse(receipt.getReceiptId()), key("shared-lock-receipt-reverse"))),
                receiptFailure, ready, start, done);
        start(ready, start, done);

        assertThat(writeOffResult.get()).isNotNull();
        assertThat(writeOffFailure.get()).isNull();
        BigDecimal effectiveReceiptAmount = jdbc.queryForObject("SELECT amount - COALESCE((SELECT SUM(amount)"
                        + " FROM finance_receipt reverse WHERE reverse.reverse_of_id = original.id"
                        + " AND reverse.entry_type = 'REVERSE' AND reverse.deleted = FALSE), 0)"
                        + " FROM finance_receipt original WHERE original.id = ?",
                BigDecimal.class, receipt.getReceiptId());
        BigDecimal usedAmount = jdbc.queryForObject("SELECT COALESCE(SUM(CASE"
                        + " WHEN entry_type = 'NORMAL' THEN amount WHEN entry_type = 'REVERSE' THEN -amount ELSE 0 END), 0)"
                        + " FROM finance_write_off WHERE source_type = 'RECEIPT' AND source_id = ? AND deleted = FALSE",
                BigDecimal.class, receipt.getReceiptId());
        assertThat(effectiveReceiptAmount.subtract(usedAmount)).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        if (receiptResult.get() != null) {
            assertThat(receiptFailure.get()).isNull();
            assertThat(effectiveReceiptAmount).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(usedAmount).isEqualByComparingTo(BigDecimal.ZERO);
        } else {
            assertBusinessCode(receiptFailure.get(), 41142);
        }
    }

    private FinanceReceiptVO receipt(String amount) {
        return receipt(newCustomer(), amount);
    }

    private FinanceReceiptVO receipt(Long customerId, String amount) {
        FinanceReceiptAddForm form = new FinanceReceiptAddForm();
        form.setCustomerId(customerId);
        form.setAmount(amount);
        form.setMethod("BANK_TRANSFER");
        form.setReceivedAt(EVENT_AT);
        return financeReceiptService.add(form, key("receipt-add"));
    }

    private Long normalReceivable(Long customerId, String amount) {
        long sourceId = uniqueNumber();
        return jdbc.queryForObject("INSERT INTO finance_receivable (receivable_no, source_type, source_id, order_id,"
                        + " customer_id, customer_name_snapshot, settlement_customer_id,"
                        + " settlement_customer_name_snapshot, entry_type, amount, event_at, created_at, updated_at)"
                        + " VALUES (?, 'SALES_ORDER', ?, 1, ?, '并发核销客户', ?, '并发核销客户', 'NORMAL', ?,"
                        + " now(), now(), now())"
                        + " RETURNING id",
                Long.class, "AR-RACE-" + prefix + "-" + UUID.randomUUID(), sourceId, customerId, customerId,
                new BigDecimal(amount));
    }

    private FinanceWriteOffAddForm writeOff(Long receiptId, Long targetId, String amount) {
        FinanceWriteOffAddItemForm item = new FinanceWriteOffAddItemForm();
        item.setTargetId(targetId);
        item.setAmount(amount);
        FinanceWriteOffAddForm form = new FinanceWriteOffAddForm();
        form.setSourceType("RECEIPT");
        form.setSourceId(receiptId);
        form.setItems(List.of(item));
        return form;
    }

    private FinanceWriteOffReverseForm writeOffReverse(Long writeOffId) {
        FinanceWriteOffReverseForm form = new FinanceWriteOffReverseForm();
        form.setWriteOffId(writeOffId);
        form.setReason("并发反向核销");
        return form;
    }

    private FinanceReceiptReverseForm receiptReverse(Long receiptId) {
        FinanceReceiptReverseForm form = new FinanceReceiptReverseForm();
        form.setReceiptId(receiptId);
        form.setReason("并发反向收款");
        return form;
    }

    private void run(String name, Runnable body, AtomicReference<Throwable> failure,
            CountDownLatch ready, CountDownLatch start, CountDownLatch done) {
        new Thread(() -> {
            login(name);
            try {
                ready.countDown();
                start.await();
                body.run();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                SmartRequestUtil.remove();
                done.countDown();
            }
        }, name).start();
    }

    private void login(String name) {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setActualName(name);
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(true);
        SmartRequestUtil.setRequestUser(employee);
    }

    private void start(CountDownLatch ready, CountDownLatch start, CountDownLatch done) throws InterruptedException {
        assertThat(ready.await(30, TimeUnit.SECONDS)).as("threads reach the same start line").isTrue();
        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).as("lock contenders finish without deadlock").isTrue();
    }

    private static void assertBusinessCode(Throwable error, int expectedCode) {
        assertThat(error).isInstanceOf(ScmBusinessException.class);
        assertThat(((ScmBusinessException) error).getErrorCode().getCode()).isEqualTo(expectedCode);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private String key(String label) {
        return prefix + ":" + label + ":" + UUID.randomUUID();
    }

    private long uniqueNumber() {
        return 1_000_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 8_000_000_000L);
    }
}
