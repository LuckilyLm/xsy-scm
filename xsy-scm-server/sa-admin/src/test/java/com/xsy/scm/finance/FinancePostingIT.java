package com.xsy.scm.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Supplier;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.finance.domain.form.FinancePostingAddForm;
import com.xsy.scm.finance.domain.form.FinancePostingQueryForm;
import com.xsy.scm.finance.domain.form.FinancePostingUpdateForm;
import com.xsy.scm.finance.domain.form.FinancePostingVoidForm;
import com.xsy.scm.finance.domain.vo.FinancePostingVO;
import com.xsy.scm.finance.service.FinancePostingQueryService;
import com.xsy.scm.finance.service.FinancePostingService;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 过账特殊单据（D-1028-16）的 PostgreSQL 集成测试。
 *
 * <p>
 * 钉住三件在单测里证明不了的事：
 * <ol>
 *   <li><b>不进账</b> —— 登记、修改、作废都不写应收/应付/核销，客户余额与账龄完全不动；</li>
 *   <li><b>留痕</b> —— 每次动作按「过账 + id」追加财务日志，改前改后都进快照；</li>
 *   <li><b>作废是事实</b> —— 作废由独立行推导，必须写原因，且不可重复作废、作废后不可修改。</li>
 * </ol>
 */
class FinancePostingIT extends ScmW5PgITBase {

    @Autowired
    private FinancePostingService postingService;

    @Autowired
    private FinancePostingQueryService postingQueryService;

    @Test
    @DisplayName("#1 登记过账只落过账表与日志，不生成应收应付、不写核销")
    void addLeavesNoLedgerEffects() {
        Long customerId = newCustomer();
        int receivablesBefore = count("SELECT count(*) FROM xsy_finance_receivable");
        int payablesBefore = count("SELECT count(*) FROM xsy_finance_payable");
        int writeOffsBefore = count("SELECT count(*) FROM xsy_finance_write_off");

        FinancePostingVO posting = postingService.add(addForm(customerId, "1234.5678"), key("add"));

        assertThat(posting.getPostingNo()).startsWith("PD");
        assertThat(posting.getStatus()).isEqualTo("NORMAL");
        assertThat(posting.getRefundStatus()).isEqualTo("PENDING");
        assertThat(posting.getTaxRate()).isEqualByComparingTo("13.0000");
        assertThat(posting.getCustomerName()).isNotBlank();
        assertThat(posting.getAmount()).isEqualByComparingTo("1234.5678");

        assertThat(count("SELECT count(*) FROM xsy_finance_receivable")).isEqualTo(receivablesBefore);
        assertThat(count("SELECT count(*) FROM xsy_finance_payable")).isEqualTo(payablesBefore);
        assertThat(count("SELECT count(*) FROM xsy_finance_write_off")).isEqualTo(writeOffsBefore);
        assertThat(count("SELECT count(*) FROM xsy_finance_operation_log WHERE business_type = 'POSTING' "
            + "AND business_id = ? AND operation_type = 'POSTING_ADD'", posting.getPostingId())).isEqualTo(1);
    }

    @Test
    @DisplayName("#2 金额、税点、返款状态与客户各自校验（41186 / 41189 / 41190 / 41191）")
    void addValidatesInputs() {
        Long customerId = newCustomer();

        FinancePostingAddForm zero = addForm(customerId, "0.0000");
        expectCode(() -> postingService.add(zero, key("zero")), 41189);

        FinancePostingAddForm tax = addForm(customerId, "10.0000");
        tax.setTaxRate("100.0001");
        expectCode(() -> postingService.add(tax, key("tax")), 41190);

        FinancePostingAddForm status = addForm(customerId, "10.0000");
        status.setRefundStatus("DONE");
        expectCode(() -> postingService.add(status, key("status")), 41191);

        FinancePostingAddForm missingCustomer = addForm(customerId, "10.0000");
        missingCustomer.setCustomerId(999_999_999L);
        expectCode(() -> postingService.add(missingCustomer, key("customer")), 41186);

        assertThat(count("SELECT count(*) FROM xsy_finance_posting WHERE customer_id = ?", customerId)).isZero();
    }

    @Test
    @DisplayName("#3 同一幂等键重复提交只登记一张过账单")
    void addIsIdempotent() {
        Long customerId = newCustomer();
        String idempotencyKey = key("add-once");

        FinancePostingVO first = postingService.add(addForm(customerId, "88.0000"), idempotencyKey);
        FinancePostingVO replay = postingService.add(addForm(customerId, "88.0000"), idempotencyKey);

        assertThat(replay.getPostingId()).isEqualTo(first.getPostingId());
        assertThat(count("SELECT count(*) FROM xsy_finance_posting WHERE customer_id = ?", customerId)).isEqualTo(1);
    }

    @Test
    @DisplayName("#4 修改写前后快照；版本过期按 40921 拒绝")
    void updateWritesSnapshotAndRejectsStaleVersion() {
        Long customerId = newCustomer();
        FinancePostingVO created = postingService.add(addForm(customerId, "100.0000"), key("update-add"));

        FinancePostingUpdateForm stale = updateForm(created);
        stale.setVersion(created.getVersion() + 1);
        stale.setAmount("200.0000");
        expectCode(() -> postingService.update(stale, key("update-stale")), 40921);
        assertThat(amountOf(created.getPostingId())).isEqualByComparingTo("100.0000");

        FinancePostingUpdateForm update = updateForm(created);
        update.setAmount("200.5000");
        update.setDocumentDate(LocalDate.of(2026, 10, 1));
        update.setRefundStatus("REFUNDED");
        update.setTaxRate("9.0000");
        update.setRemark("按更正后的单据修改");
        FinancePostingVO updated = postingService.update(update, key("update"));

        assertThat(updated.getAmount()).isEqualByComparingTo("200.5000");
        assertThat(updated.getDocumentDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(updated.getRefundStatus()).isEqualTo("REFUNDED");
        assertThat(updated.getVersion()).isGreaterThan(created.getVersion());

        assertThat(logValue(created.getPostingId(), "POSTING_UPDATE", "before_data", "amount"))
            .isEqualTo("100.0000");
        assertThat(logValue(created.getPostingId(), "POSTING_UPDATE", "after_data", "amount"))
            .isEqualTo("200.5000");
    }

    @Test
    @DisplayName("#5 作废必须写原因（41187），作废后不可修改或重复作废（41185）")
    void voidRequiresReasonAndIsTerminal() {
        Long customerId = newCustomer();
        FinancePostingVO created = postingService.add(addForm(customerId, "66.0000"), key("void-add"));

        FinancePostingVoidForm noReason = voidForm(created, null);
        expectCode(() -> postingService.voidPosting(noReason, key("void-no-reason")), 41187);
        assertThat(statusOf(created.getPostingId())).isEqualTo("NORMAL");

        FinancePostingVO voided = postingService.voidPosting(voidForm(created, "金额录错，重新登记"), key("void"));
        assertThat(voided.getStatus()).isEqualTo("VOIDED");
        assertThat(voided.getVoidReason()).isEqualTo("金额录错，重新登记");
        assertThat(voided.getVoidedAt()).isNotNull();
        assertThat(count("SELECT count(*) FROM xsy_finance_posting_void WHERE posting_id = ?",
            created.getPostingId())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM xsy_finance_operation_log WHERE business_type = 'POSTING' "
            + "AND business_id = ? AND operation_type = 'POSTING_VOID'", created.getPostingId())).isEqualTo(1);

        expectCode(() -> postingService.voidPosting(voidForm(created, "再作废一次"), key("void-again")), 41185);
        FinancePostingUpdateForm update = updateForm(created);
        update.setAmount("70.0000");
        expectCode(() -> postingService.update(update, key("update-voided")), 41185);

        // 作废是事实不是删除：过账行仍在，金额不被改写
        assertThat(amountOf(created.getPostingId())).isEqualByComparingTo("66.0000");
    }

    @Test
    @DisplayName("#6 数据范围：看不到客户归属的过账，也不能给它登记")
    void scopeHidesOtherSellersPostings() {
        Long customerId = newCustomer();
        FinancePostingVO created = postingService.add(addForm(customerId, "12.0000"), key("scope-add"));

        asEmployee(99001L, () -> {
            assertThatThrownBy(() -> postingService.add(addForm(customerId, "1.0000"), key("scope-other")))
                .isExactlyInstanceOf(ScmDataScopeException.class);
            assertThat(pageRows(null)).isZero();
            expectCode(() -> postingQueryService.detail(created.getPostingId()), 41184);
            return null;
        });

        assertThat(pageRows(created.getPostingNo())).isEqualTo(1);
    }

    @Test
    @DisplayName("#7 查询能按返款状态与有效状态筛选，作废行不会悄悄消失")
    void queryFiltersByRefundStatusAndValidity() {
        Long customerId = newCustomer();
        FinancePostingVO normal = postingService.add(addForm(customerId, "30.0000"), key("filter-1"));
        FinancePostingVO second = postingService.add(addForm(customerId, "40.0000"), key("filter-2"));
        FinancePostingVO voided = postingService.update(updateWithRefundStatus(second, "PARTIAL"),
            key("filter-partial"));
        postingService.voidPosting(voidForm(voided, "重复登记"), key("filter-void"));

        FinancePostingQueryForm all = queryForm();
        all.setCustomerId(customerId);
        assertThat(postingQueryService.query(all).getTotal()).isEqualTo(2);

        FinancePostingQueryForm valid = queryForm();
        valid.setCustomerId(customerId);
        valid.setStatus("NORMAL");
        assertThat(postingQueryService.query(valid).getList()).extracting(FinancePostingVO::getPostingId)
            .containsExactly(normal.getPostingId());

        FinancePostingQueryForm refunded = queryForm();
        refunded.setCustomerId(customerId);
        refunded.setRefundStatus("PARTIAL");
        assertThat(postingQueryService.query(refunded).getList()).extracting(FinancePostingVO::getPostingId)
            .containsExactly(voided.getPostingId());
    }

    private FinancePostingAddForm addForm(Long customerId, String amount) {
        FinancePostingAddForm form = new FinancePostingAddForm();
        form.setCustomerId(customerId);
        form.setDocumentDate(LocalDate.of(2026, 10, 28));
        form.setAmount(amount);
        form.setTaxRate("13");
        form.setRemark(prefix + " 过账");
        return form;
    }

    private FinancePostingUpdateForm updateForm(FinancePostingVO posting) {
        FinancePostingUpdateForm form = new FinancePostingUpdateForm();
        form.setPostingId(posting.getPostingId());
        form.setVersion(posting.getVersion());
        form.setDocumentDate(posting.getDocumentDate());
        form.setAmount(posting.getAmount().toPlainString());
        form.setTaxRate(posting.getTaxRate() == null ? null : posting.getTaxRate().toPlainString());
        form.setRefundStatus(posting.getRefundStatus());
        form.setRemark(posting.getRemark());
        return form;
    }

    private FinancePostingUpdateForm updateWithRefundStatus(FinancePostingVO posting, String refundStatus) {
        FinancePostingUpdateForm form = updateForm(posting);
        form.setRefundStatus(refundStatus);
        return form;
    }

    private FinancePostingVoidForm voidForm(FinancePostingVO posting, String reason) {
        FinancePostingVoidForm form = new FinancePostingVoidForm();
        form.setPostingId(posting.getPostingId());
        form.setVersion(posting.getVersion());
        form.setReason(reason);
        return form;
    }

    private FinancePostingQueryForm queryForm() {
        FinancePostingQueryForm form = new FinancePostingQueryForm();
        form.setStartDate(LocalDate.of(2026, 9, 1));
        form.setEndDate(LocalDate.of(2026, 11, 30));
        form.setPageNum(1L);
        form.setPageSize(20L);
        return form;
    }

    private int pageRows(String keyword) {
        FinancePostingQueryForm form = queryForm();
        form.setKeyword(keyword);
        return postingQueryService.query(form).getList().size();
    }

    private java.math.BigDecimal amountOf(Long postingId) {
        return jdbc.queryForObject("SELECT amount FROM xsy_finance_posting WHERE id = ?",
            java.math.BigDecimal.class, postingId);
    }

    private String statusOf(Long postingId) {
        return postingQueryService.detail(postingId).getStatus();
    }

    private String logValue(Long postingId, String operationType, String column, String key) {
        return jdbc.queryForObject("SELECT " + column + "->>'" + key + "' FROM xsy_finance_operation_log "
            + "WHERE business_type = 'POSTING' AND business_id = ? AND operation_type = ?",
            String.class, postingId, operationType);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private String key(String tag) {
        return prefix + ":" + tag + ":" + UUID.randomUUID();
    }

    /**
     * 以非超管员工执行：数据范围按「归属业务员 = 自己」解析，客户归属别人就既读不到也写不了。
     */
    private <T> T asEmployee(Long employeeId, Supplier<T> body) {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(employeeId);
        employee.setActualName("过账数据范围 IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(false);
        SmartRequestUtil.setRequestUser(employee);
        try {
            return body.get();
        } finally {
            RequestEmployee admin = new RequestEmployee();
            admin.setEmployeeId(1L);
            admin.setActualName("过账 IT 操作员");
            admin.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            admin.setAdministratorFlag(true);
            SmartRequestUtil.setRequestUser(admin);
        }
    }
}
