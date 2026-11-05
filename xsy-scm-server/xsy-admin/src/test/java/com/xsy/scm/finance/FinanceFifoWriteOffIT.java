package com.xsy.scm.finance;

import static org.assertj.core.api.Assertions.assertThat;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.finance.dao.FinanceReceivableDao;
import com.xsy.scm.finance.domain.form.FinanceReceiptAddForm;
import com.xsy.scm.finance.domain.form.FinanceWriteOffAutoForm;
import com.xsy.scm.finance.domain.vo.FinanceReceiptVO;
import com.xsy.scm.finance.domain.vo.FinanceWriteOffAddResultVO;
import com.xsy.scm.finance.domain.vo.FinanceWriteOffVO;
import com.xsy.scm.finance.service.FinanceReceiptService;
import com.xsy.scm.finance.service.FinanceWriteOffService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 收款自动 FIFO 核销。
 *
 * <p>顺序固定为账单日期、单据 id 升序；一次最多分配 100 个目标，剩余额留在收款上。
 * 自动核销仍写普通的 NORMAL 核销行，不另建一套记录表。
 */
class FinanceFifoWriteOffIT extends ScmW5PgITBase {

    private static final OffsetDateTime EVENT_AT = OffsetDateTime.of(2026, 3, 17, 9, 0, 0, 0,
        ZoneOffset.ofHours(8));

    @Autowired
    private FinanceReceiptService financeReceiptService;

    @Autowired
    private FinanceWriteOffService financeWriteOffService;

    @Autowired
    private FinanceReceivableDao receivables;

    @Test
    void groupedCreditsStayWithTheirCustomerAndAreAppliedBeforeCandidateLimit() {
        Long parent = newCustomer();
        Long firstCustomer = newCustomer();
        Long secondCustomer = newCustomer();
        normalReceivable(firstCustomer, "100", EVENT_AT.minusDays(10), parent);
        Long remaining = normalReceivable(firstCustomer, "50", EVENT_AT.minusDays(5), parent);
        Long other = normalReceivable(secondCustomer, "100", EVENT_AT.minusDays(3), parent);
        customerCredit(firstCustomer, parent, "120");
        customerCredit(parent, parent, "999");
        evictMybatisCache();
        var one = receivables.selectAutoTargets(parent, ScmValueScope.all(), 1);
        assertThat(one).hasSize(1);
        assertThat(one.getFirst().getTargetId()).isEqualTo(remaining);
        assertThat(one.getFirst().getOpenAmount()).isEqualByComparingTo("30");
        var all = receivables.selectAutoTargets(parent, ScmValueScope.all(), 100);
        assertThat(all).extracting(row -> row.getTargetId()).containsExactly(remaining, other);
        assertThat(all.get(1).getOpenAmount()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("#1 自动核销按账单日期从旧到新分配，剩余额不落到更晚的单据")
    void autoWriteOffFollowsBillDateOrder() {
        Long customerId = newCustomer();
        Long oldest = normalReceivable(customerId, "30.0000", EVENT_AT.minusDays(10));
        Long middle = normalReceivable(customerId, "30.0000", EVENT_AT.minusDays(5));
        Long newest = normalReceivable(customerId, "30.0000", EVENT_AT.minusDays(1));
        FinanceReceiptVO receipt = addReceipt(customerId, "50.0000");

        FinanceWriteOffAutoForm previewForm = autoForm(receipt.getReceiptId());
        var plan = financeWriteOffService.autoPreview(previewForm);
        assertThat(plan).extracting(row -> row.getTargetId()).containsExactly(oldest, middle);
        assertThat(plan).extracting(row -> row.getAllocatedAmount())
            .containsExactly(new BigDecimal("30.0000"), new BigDecimal("20.0000"));

        FinanceWriteOffAddResultVO result = financeWriteOffService.autoAdd(previewForm, key("fifo"));
        assertThat(result.getItems()).extracting(FinanceWriteOffVO::getTargetId)
            .containsExactly(oldest, middle);
        assertThat(result.getItems()).extracting(FinanceWriteOffVO::getAmount)
            .containsExactly(new BigDecimal("30.0000"), new BigDecimal("20.0000"));
        assertThat(allocatedTo(newest)).isZero();
    }

    @Test
    @DisplayName("#2 同一防重复请求编号重放不重复核销；金额分完后再次自动核销返回 41164")
    void replayIsIdempotentAndExhaustedReceiptIsRejected() {
        Long customerId = newCustomer();
        normalReceivable(customerId, "40.0000", EVENT_AT.minusDays(3));
        FinanceReceiptVO receipt = addReceipt(customerId, "40.0000");
        FinanceWriteOffAutoForm form = autoForm(receipt.getReceiptId());
        String idempotencyKey = key("fifo-replay");

        FinanceWriteOffAddResultVO first = financeWriteOffService.autoAdd(form, idempotencyKey);
        FinanceWriteOffAddResultVO replay = financeWriteOffService.autoAdd(form, idempotencyKey);

        assertThat(replay.getItems()).extracting(FinanceWriteOffVO::getWriteOffId)
            .containsExactlyElementsOf(first.getItems().stream().map(FinanceWriteOffVO::getWriteOffId).toList());
        assertThat(count("SELECT count(*) FROM xsy_finance_write_off WHERE source_type = 'RECEIPT' "
            + "AND source_id = ? AND entry_type = 'NORMAL'", receipt.getReceiptId())).isEqualTo(1);

        expectCode(() -> financeWriteOffService.autoAdd(form, key("fifo-exhausted")), 41164);
    }

    private FinanceWriteOffAutoForm autoForm(Long receiptId) {
        FinanceWriteOffAutoForm form = new FinanceWriteOffAutoForm();
        form.setSourceType("RECEIPT");
        form.setSourceId(receiptId);
        return form;
    }

    private FinanceReceiptVO addReceipt(Long customerId, String amount) {
        FinanceReceiptAddForm form = new FinanceReceiptAddForm();
        form.setCustomerId(customerId);
        form.setAmount(amount);
        form.setMethod("BANK_TRANSFER");
        form.setReceivedAt(EVENT_AT);
        return financeReceiptService.add(form, key("receipt"));
    }

    private Long normalReceivable(Long customerId, String amount, OffsetDateTime eventAt) {
        return normalReceivable(customerId, amount, eventAt, customerId);
    }

    private void customerCredit(Long customerId, Long settlementId, String amount) {
        jdbc.update("INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id, customer_id, "
            + "customer_name_snapshot, settlement_customer_id, settlement_customer_name_snapshot, entry_type, amount, event_at, reason) "
            + "VALUES (?, 'ORDER_RETURN', ?, ?, 'FIFO客户', ?, 'FIFO集团', 'RED', ?, ?, '独立退货')",
            prefix + "-credit-" + UUID.randomUUID(), uniqueNumber(), customerId, settlementId,
            new BigDecimal(amount), EVENT_AT);
    }

    private Long normalReceivable(Long customerId, String amount, OffsetDateTime eventAt, Long settlementId) {
        long sourceId = uniqueNumber();
        return jdbc.queryForObject("INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id,"
                + " order_id, customer_id, customer_name_snapshot, settlement_customer_id,"
                + " settlement_customer_name_snapshot, entry_type, amount, event_at, created_at, updated_at)"
                + " VALUES (?, 'SALES_ORDER', ?, 1, ?, 'FIFO 测试客户', ?, 'FIFO 测试客户', 'NORMAL', ?, ?, now(), now())"
                + " RETURNING id",
            Long.class, "AR-FIFO-" + prefix + "-" + UUID.randomUUID(), sourceId, customerId, settlementId,
            new BigDecimal(amount), eventAt);
    }

    private BigDecimal allocatedTo(Long receivableId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM xsy_finance_write_off "
            + "WHERE target_type = 'RECEIVABLE' AND target_id = ? AND entry_type = 'NORMAL'",
            BigDecimal.class, receivableId);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private String key(String tag) {
        return prefix + ":" + tag + ":" + UUID.randomUUID();
    }

    private long uniqueNumber() {
        return 1_000_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 8_000_000_000L);
    }

}
