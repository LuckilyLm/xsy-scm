package com.xsy.scm.finance;

import com.xsy.scm.finance.dao.FinancePayableDao;
import com.xsy.scm.finance.dao.FinanceSettlementRuleDao;
import com.xsy.scm.finance.domain.dto.FinanceCreditRuleDto;
import com.xsy.scm.finance.domain.entity.FinancePayableEntity;
import com.xsy.scm.finance.domain.entity.FinanceReceivableEntity;
import com.xsy.scm.finance.service.FinanceSettlementRuleService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 在无数据库触发器的条件下验证入账时点、账期快照和红字继承。 */
class FinanceSettlementRuleServiceTest {
    private final FinanceSettlementRuleDao rules = mock(FinanceSettlementRuleDao.class);
    private final FinancePayableDao payables = mock(FinancePayableDao.class);
    private final FinanceSettlementRuleService service = new FinanceSettlementRuleService(rules, payables);

    @Test
    void dailyCreditUsesShanghaiBusinessDateAndFreezesRule() {
        FinanceCreditRuleDto rule = rule("DAY", 10, null);
        when(rules.selectCreditRule(1L)).thenReturn(rule);
        FinanceReceivableEntity row = receivable();
        service.freezeReceivable(row);
        assertThat(row.getDueDate()).isEqualTo(LocalDate.of(2026, 2, 11));
        assertThat(row.getCreditRuleSnapshot()).containsEntry("type", "BY_TIME").containsEntry("value", 10);
        rule.setValue(90);
        assertThat(row.getCreditRuleSnapshot()).containsEntry("value", 10);
    }

    @Test
    void monthlyCreditUsesTargetMonthAndSettlementDay() {
        when(rules.selectCreditRule(1L)).thenReturn(rule("MONTH", 1, 15));
        FinanceReceivableEntity row = receivable();
        service.freezeReceivable(row);
        assertThat(row.getDueDate()).isEqualTo(LocalDate.of(2026, 3, 15));
    }

    @Test
    void creditWithoutSettlementDayUsesFirstDayOfTargetMonth() {
        when(rules.selectCreditRule(1L)).thenReturn(rule("MONTH", 1, null));
        FinanceReceivableEntity row = receivable();
        service.freezeReceivable(row);
        assertThat(row.getDueDate()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void amountCreditHasSnapshotAndNoDueDate() {
        FinanceCreditRuleDto rule = new FinanceCreditRuleDto();
        rule.setType("BY_AMOUNT");
        when(rules.selectCreditRule(1L)).thenReturn(rule);
        FinanceReceivableEntity row = receivable();
        service.freezeReceivable(row);
        assertThat(row.getDueDate()).isNull();
        assertThat(row.getCreditRuleSnapshot()).containsEntry("type", "BY_AMOUNT");
    }

    @Test
    void payableFreezesSupplierPeriodOnShanghaiBusinessDate() {
        when(rules.selectPaymentDays(2L)).thenReturn(30);
        FinancePayableEntity row = payable();
        service.freezePayable(row);
        assertThat(row.getDueDate()).isEqualTo(LocalDate.of(2026, 3, 3));
        assertThat(row.getPaymentRuleSnapshot()).containsEntry("paymentPeriodDays", 30);
    }

    @Test
    void redPayableInheritsOriginalInsteadOfCurrentSupplierRule() {
        FinancePayableEntity original = payable();
        original.setDueDate(LocalDate.of(2026, 2, 20));
        original.setPaymentRuleSnapshot(Map.of("paymentPeriodDays", 19));
        when(payables.selectById(7L)).thenReturn(original);
        FinancePayableEntity row = payable();
        row.setEntryType("RED");
        row.setOriginalPayableId(7L);
        service.freezePayable(row);
        assertThat(row.getDueDate()).isEqualTo(original.getDueDate());
        assertThat(row.getPaymentRuleSnapshot()).isEqualTo(original.getPaymentRuleSnapshot());
        verifyNoInteractions(rules);
    }

    @Test
    void missingOriginalAndInvalidPeriodFailBeforeWriting() {
        FinancePayableEntity red = payable();
        red.setEntryType("RED");
        red.setOriginalPayableId(99L);
        assertThatThrownBy(() -> service.freezePayable(red)).isInstanceOf(IllegalStateException.class);
        when(rules.selectCreditRule(1L)).thenReturn(rule("DAY", -1, null));
        assertThatThrownBy(() -> service.freezeReceivable(receivable())).isInstanceOf(IllegalStateException.class);
    }

    private FinanceCreditRuleDto rule(String unit, int value, Integer day) {
        FinanceCreditRuleDto rule = new FinanceCreditRuleDto();
        rule.setType("BY_TIME");
        rule.setUnit(unit);
        rule.setValue(value);
        rule.setSettleDay(day);
        return rule;
    }

    private FinanceReceivableEntity receivable() {
        FinanceReceivableEntity row = new FinanceReceivableEntity();
        row.setSettlementCustomerId(1L);
        row.setEventAt(OffsetDateTime.parse("2026-01-31T20:00:00Z"));
        return row;
    }

    private FinancePayableEntity payable() {
        FinancePayableEntity row = new FinancePayableEntity();
        row.setSupplierId(2L);
        row.setEntryType("NORMAL");
        row.setEventAt(OffsetDateTime.parse("2026-01-31T20:00:00Z"));
        return row;
    }
}
