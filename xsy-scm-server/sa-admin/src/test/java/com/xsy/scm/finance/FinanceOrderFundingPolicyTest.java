package com.xsy.scm.finance;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.finance.constant.FinanceErrorCode;
import com.xsy.scm.finance.dao.FinanceOrderFundingSourceDao;
import com.xsy.scm.finance.domain.dto.FinanceOrderFundingDto;
import com.xsy.scm.finance.service.FinanceOrderFundingPolicy;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinanceOrderFundingPolicyTest {
    private final FinanceOrderFundingSourceDao sources = mock(FinanceOrderFundingSourceDao.class);
    private final FinanceOrderFundingPolicy policy = new FinanceOrderFundingPolicy(sources);

    @Test
    void balanceFundingCannotUseExternalRefundEvenWhenAnOnlineTransactionWasSelected() {
        when(sources.selectOrderFunding(10L)).thenReturn(List.of(balance(), online()));
        assertThatThrownBy(() -> policy.requireCashRefundAllowed(10L, true))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(FinanceErrorCode.REFUND_ALLOCATION_REQUIRED));
    }

    @Test
    void pureBalanceCannotUseManualCashRefund() {
        when(sources.selectOrderFunding(10L)).thenReturn(List.of(balance()));
        assertThatThrownBy(() -> policy.requireCashRefundAllowed(10L, false))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(FinanceErrorCode.BALANCE_REFUND_NOT_ENABLED));
    }

    @Test
    void offlineManualRefundRemainsAvailableWithoutPaymentTransactions() {
        when(sources.selectOrderFunding(10L)).thenReturn(List.of());
        assertThatCode(() -> policy.requireCashRefundAllowed(10L, false)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireCashRefundAllowed(10L, true)).isInstanceOf(ScmBusinessException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"movement", "amount", "settlement", "receipt", "provider", "status", "customer"})
    void rejectsIncompleteOrMismatchedBalanceFacts(String mismatch) {
        var fact = balance();
        switch (mismatch) {
            case "movement" -> fact.setMovementId(null);
            case "amount" -> fact.setMovementAmount(new BigDecimal("99"));
            case "settlement" -> fact.setMovementSettlementCustomerId(999L);
            case "receipt" -> fact.setReceiptId(99L);
            case "provider" -> fact.setProvider("MOCK");
            case "status" -> fact.setTransactionStatus("PENDING");
            case "customer" -> fact.setMovementCustomerId(999L);
            default -> throw new IllegalArgumentException(mismatch);
        }
        assertThatThrownBy(() -> policy.requireSuccessful(fact)).isInstanceOf(ScmBusinessException.class);
    }

    @Test
    void onlineFundingUsesActualAmountAndRequiresReceiptBeforeRefund() {
        var fact = online();
        fact.setProviderAmount(new BigDecimal("98")); fact.setReceiptAmount(new BigDecimal("98"));
        when(sources.selectOrderFunding(10L)).thenReturn(List.of(fact));
        assertThatCode(() -> policy.requireCashRefundAllowed(10L, true)).doesNotThrowAnyException();
        fact.setReceiptId(null);
        assertThatThrownBy(() -> policy.requireCashRefundAllowed(10L, true)).isInstanceOf(ScmBusinessException.class);
    }

    static FinanceOrderFundingDto balance() {
        var fact = online();
        fact.setMethod("BALANCE"); fact.setProvider("INTERNAL_BALANCE"); fact.setTransactionProvider("INTERNAL_BALANCE");
        fact.setReceiptId(null); fact.setMovementId(30L); fact.setMovementCustomerId(2L);
        fact.setMovementSettlementCustomerId(3L); fact.setMovementIntentId(1L);
        fact.setMovementType("CONSUME"); fact.setMovementDirection("DEBIT"); fact.setMovementSourceType("PAYMENT_INTENT");
        fact.setMovementAmount(new BigDecimal("100.0000"));
        fact.setMovementOccurredAt(fact.getPaidAt());
        return fact;
    }

    static FinanceOrderFundingDto online() {
        var fact = new FinanceOrderFundingDto();
        fact.setIntentId(1L); fact.setTransactionId(20L); fact.setOrderId(10L);
        fact.setCustomerId(2L); fact.setSettlementCustomerId(3L); fact.setSourceType("SALES_ORDER");
        fact.setMethod("ONLINE"); fact.setProvider("MOCK"); fact.setTransactionProvider("MOCK");
        fact.setIntentStatus("SUCCEEDED"); fact.setTransactionStatus("SUCCEEDED");
        fact.setAmount(new BigDecimal("100.0000")); fact.setProviderAmount(new BigDecimal("100.0000"));
        fact.setPaidAt(OffsetDateTime.parse("2026-10-04T12:00:00+08:00")); fact.setReceiptId(40L);
        fact.setTransactionAmount(fact.getAmount());
        fact.setReceiptCustomerId(2L); fact.setReceiptSettlementCustomerId(3L); fact.setReceiptAmount(fact.getProviderAmount());
        return fact;
    }
}
