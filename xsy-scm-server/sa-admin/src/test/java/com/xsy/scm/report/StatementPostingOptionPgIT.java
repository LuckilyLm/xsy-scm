package com.xsy.scm.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.finance.domain.form.FinancePostingAddForm;
import com.xsy.scm.finance.domain.form.FinancePostingVoidForm;
import com.xsy.scm.finance.domain.vo.FinancePostingVO;
import com.xsy.scm.finance.service.FinancePostingService;
import com.xsy.scm.report.domain.form.ScmCustomerStatementForm;
import com.xsy.scm.report.domain.vo.ScmCustomerStatementItemVO;
import com.xsy.scm.report.domain.vo.ScmCustomerStatementVO;
import com.xsy.scm.report.service.ScmCustomerStatementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 对账单可选展示过账记录（D-1028-16）。
 *
 * <p>要钉住的是「可选中包含」这件事的边界：过账行进得了明细，但它不改变任何余额口径 ——
 * 期初、期末净应收与未分配资金在有/无过账行时完全相同。
 */
class StatementPostingOptionPgIT extends ScmW5PgITBase {

    @Autowired
    private ScmCustomerStatementService statementService;

    @Autowired
    private FinancePostingService postingService;

    @Test
    @DisplayName("#1 不勾选过账时不出现过账行；勾选后过账行进入明细但余额口径不变")
    void postingLinesDoNotChangeBalances() {
        Long customerId = newCustomer();
        insertReceipt(customerId, "50.0000");
        postingService.add(postingForm(customerId, "30.0000"), key("add"));

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
        // 过账行的余额列与资金变动列都停在原值：它不是财务事实
        ScmCustomerStatementItemVO line = postingLine(withPosting);
        assertThat(line.getPostingAmount()).isEqualByComparingTo("30.0000");
        assertThat(line.getReceivableDelta()).isZero();
        assertThat(line.getReceiptDelta()).isZero();
        assertThat(line.getWriteOffDelta()).isZero();
        assertThat(line.getRefundDelta()).isZero();
        assertThat(line.getReceivableBalance()).isZero();
        // 过账金额真正落库，明细与打印都从冻结行读
        assertThat(jdbc.queryForObject(
            "SELECT posting_amount FROM xsy_customer_statement_item WHERE statement_id = ? AND fact_type = 'POSTING'",
            BigDecimal.class, withPosting.getId())).isEqualByComparingTo("30.0000");
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

        ScmCustomerStatementVO statement = statementService.freeze(statementForm(customerId, true));
        assertThat(factsOf(statement)).containsExactly("RECEIPT");
    }

    @Test
    @DisplayName("#3 冻结版本记住 includePosting，重新读取时口径一致")
    void frozenVersionRemembersTheOption() {
        Long customerId = newCustomer();
        insertReceipt(customerId, "10.0000");
        postingService.add(postingForm(customerId, "5.0000"), key("read-add"));

        ScmCustomerStatementVO created = statementService.freeze(statementForm(customerId, true));
        ScmCustomerStatementVO reloaded = statementService.loadForPrint(created.getId());
        assertThat(reloaded.getIncludePosting()).isTrue();
        assertThat(factsOf(reloaded)).containsExactlyInAnyOrder("RECEIPT", "POSTING");
        assertThat(postingLine(reloaded).getPostingAmount()).isEqualByComparingTo("5.0000");
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
     * 造一笔未核销收款：对账单的 RECEIPT 事实不依赖订单，用它做最小的可对账底数。
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
