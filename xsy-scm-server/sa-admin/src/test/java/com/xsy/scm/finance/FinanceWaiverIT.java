package com.xsy.scm.finance;

import static org.assertj.core.api.Assertions.assertThat;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.finance.domain.form.FinanceWaiverAddForm;
import com.xsy.scm.finance.domain.form.FinanceWaiverReverseForm;
import com.xsy.scm.finance.domain.form.FinanceWriteOffReverseForm;
import com.xsy.scm.finance.domain.vo.FinanceWaiverVO;
import com.xsy.scm.finance.service.FinanceWaiverService;
import com.xsy.scm.finance.service.FinanceWriteOffService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 抹零与反向：抹零建模为 WAIVER 来源的核销行，冲减应收；反向追加 REVERSE，不删历史。
 *
 * <p>通用核销反向不能撤销抹零，必须走抹零自己的反向接口，否则会把减免伪装成收款退回。
 */
class FinanceWaiverIT extends ScmW5PgITBase {

    @Autowired
    private FinanceWaiverService financeWaiverService;

    @Autowired
    private FinanceWriteOffService financeWriteOffService;

    @Test
    @DisplayName("#1 抹零冲减应收并写 WAIVER 核销；超未结额被拒（41157）")
    void waiverReducesOpenAmount() {
        Long receivableId = normalReceivable("100.0000");

        FinanceWaiverVO waiver = financeWaiverService.add(waiverForm(receivableId, "0.3000", "分角抹零"),
            key("waiver-add"));

        assertThat(waiver.getReceivableId()).isEqualTo(receivableId);
        assertThat(waiver.getAmount()).isEqualByComparingTo("0.3000");
        assertThat(waiver.getWriteOffId()).isNotNull();
        assertThat(netAllocated(receivableId)).isEqualByComparingTo("0.3000");
        assertThat(count("SELECT count(*) FROM xsy_finance_write_off WHERE source_type = 'WAIVER' "
            + "AND source_id = ? AND target_id = ? AND entry_type = 'NORMAL'",
            waiver.getWaiverId(), receivableId)).isEqualTo(1);

        expectCode(() -> financeWaiverService.add(waiverForm(receivableId, "200.0000", "超额抹零"),
            key("waiver-over")), 41157);
        assertThat(netAllocated(receivableId)).isEqualByComparingTo("0.3000");
    }

    @Test
    @DisplayName("#2 反向必须写原因（41138），反向追加 REVERSE 并恢复未结额，重复反向被拒（41159）")
    void reverseRestoresOpenAmount() {
        Long receivableId = normalReceivable("80.0000");
        FinanceWaiverVO waiver = financeWaiverService.add(waiverForm(receivableId, "0.5000", "分角抹零"),
            key("waiver-reverse-add"));

        FinanceWaiverReverseForm reverse = reverseForm(waiver.getWaiverId(), null);
        expectCode(() -> financeWaiverService.reverse(reverse, key("waiver-reverse-no-reason")), 41138);
        assertThat(netAllocated(receivableId)).isEqualByComparingTo("0.5000");

        reverse.setReason("客户要求恢复应收");
        FinanceWaiverVO reversed = financeWaiverService.reverse(reverse, key("waiver-reverse"));
        assertThat(reversed.getStatus()).isEqualTo("REVERSED");
        assertThat(netAllocated(receivableId)).isZero();
        assertThat(count("SELECT count(*) FROM xsy_finance_write_off WHERE source_type = 'WAIVER' "
            + "AND source_id = ? AND entry_type = 'REVERSE'", waiver.getWaiverId())).isEqualTo(1);

        expectCode(() -> financeWaiverService.reverse(reverse, key("waiver-reverse-again")), 41159);
    }

    @Test
    @DisplayName("#3 通用核销反向接口不能撤销抹零（41161）")
    void genericWriteOffReverseCannotReverseWaiver() {
        Long receivableId = normalReceivable("50.0000");
        FinanceWaiverVO waiver = financeWaiverService.add(waiverForm(receivableId, "0.2000", "分角抹零"),
            key("waiver-generic-add"));

        FinanceWriteOffReverseForm form = new FinanceWriteOffReverseForm();
        form.setWriteOffId(waiver.getWriteOffId());
        form.setReason("误操作");
        expectCode(() -> financeWriteOffService.reverse(form, key("waiver-generic-reverse")), 41161);
        assertThat(netAllocated(receivableId)).isEqualByComparingTo("0.2000");
    }

    private FinanceWaiverAddForm waiverForm(Long receivableId, String amount, String reason) {
        FinanceWaiverAddForm form = new FinanceWaiverAddForm();
        form.setReceivableId(receivableId);
        form.setAmount(amount);
        form.setReason(reason);
        return form;
    }

    private FinanceWaiverReverseForm reverseForm(Long waiverId, String reason) {
        FinanceWaiverReverseForm form = new FinanceWaiverReverseForm();
        form.setWaiverId(waiverId);
        form.setReason(reason);
        return form;
    }

    private Long normalReceivable(String amount) {
        Long customerId = newCustomer();
        long sourceId = 1_000_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 8_000_000_000L);
        return jdbc.queryForObject("INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id,"
                + " order_id, customer_id, customer_name_snapshot, settlement_customer_id,"
                + " settlement_customer_name_snapshot, entry_type, amount, event_at, created_at, updated_at)"
                + " VALUES (?, 'SALES_ORDER', ?, 1, ?, '抹零测试客户', ?, '抹零测试客户', 'NORMAL', ?,"
                + " now(), now(), now()) RETURNING id",
            Long.class, "AR-WV-" + prefix + "-" + UUID.randomUUID(), sourceId, customerId, customerId,
            new BigDecimal(amount));
    }

    /** NORMAL 为正、REVERSE 为负，得到当前净核销额。 */
    private BigDecimal netAllocated(Long receivableId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN entry_type = 'NORMAL' THEN amount "
            + "ELSE -amount END), 0) FROM xsy_finance_write_off "
            + "WHERE target_type = 'RECEIVABLE' AND target_id = ?", BigDecimal.class, receivableId);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private String key(String tag) {
        return prefix + ":" + tag + ":" + UUID.randomUUID();
    }
}
