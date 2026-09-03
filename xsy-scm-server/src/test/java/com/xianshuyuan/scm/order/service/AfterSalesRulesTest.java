package com.xianshuyuan.scm.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AfterSalesRulesTest {
    @Test
    void rejectsARequestThatExceedsActualQuantityAfterReservations() {
        assertThatThrownBy(() -> AfterSalesRules.requireWithinActual(
                new BigDecimal("10.0000"), new BigDecimal("7.0000"), new BigDecimal("3.0001")))
                .isInstanceOf(AfterSalesRuleException.class);
    }

    @Test
    void pendingAndApprovedReservationsCanExactlyReachActualQuantity() {
        AfterSalesRules.requireWithinActual(
                new BigDecimal("10.0000"), new BigDecimal("7.0000"), new BigDecimal("3.0000"));
    }

    @Test
    void approvalAllowsZeroLinesButRequiresOnePositiveQuantity() {
        assertThatThrownBy(() -> AfterSalesRules.requirePositiveApproval(List.of(
                BigDecimal.ZERO, new BigDecimal("0.0000"))))
                .isInstanceOf(AfterSalesRuleException.class);
        AfterSalesRules.requirePositiveApproval(List.of(BigDecimal.ZERO, new BigDecimal("1.0000")));
    }

    @Test
    void approvedAmountUsesLockedPriceAndFourDecimalHalfUpRounding() {
        assertThat(AfterSalesRules.amount(new BigDecimal("1.2345"), new BigDecimal("2.3456")))
                .isEqualByComparingTo("2.8956");
    }
}
