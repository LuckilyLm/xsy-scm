package com.xsy.scm.balance;

import com.xsy.scm.balance.constant.BalanceErrorCode;
import com.xsy.scm.balance.dao.BalanceRefundSourceDao;
import com.xsy.scm.balance.dao.CustomerBalanceMovementDao;
import com.xsy.scm.balance.domain.dto.BalanceRefundFact;
import com.xsy.scm.balance.domain.entity.CustomerBalanceMovementEntity;
import com.xsy.scm.balance.service.BalanceRefundService;
import com.xsy.scm.balance.service.CustomerBalanceService;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.finance.domain.dto.FinanceOrderFundingDto;
import com.xsy.scm.finance.service.FinanceOrderFundingPolicy;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BalanceRefundServiceTest {
    private final BalanceRefundSourceDao sources = mock(BalanceRefundSourceDao.class);
    private final CustomerBalanceMovementDao movements = mock(CustomerBalanceMovementDao.class);
    private final CustomerBalanceService wallet = mock(CustomerBalanceService.class);
    private final FinanceOrderFundingPolicy fundingPolicy = mock(FinanceOrderFundingPolicy.class);
    private final BalanceRefundService service = new BalanceRefundService(sources, movements, wallet,
            fundingPolicy, mock(ScmDataScopeService.class), mock(ScmIdempotencyService.class));
    private final OffsetDateTime completedAt = OffsetDateTime.parse("2026-10-04T15:00:00+08:00");

    @BeforeEach
    void refund() {
        when(sources.selectRefund(50L)).thenReturn(new BalanceRefundFact(50L, 10L, 2L, 3L, 9L,
                new BigDecimal("30"), "COMPLETED", completedAt));
        when(sources.lockOrder(10L)).thenReturn(10L);
        when(fundingPolicy.requireCompleteOrderFunding(10L)).thenReturn(List.of(balance(1L, "60"), balance(2L, "40")));
        when(sources.returnedAmount(10L)).thenReturn(new BigDecimal("20"));
    }

    @Test
    void partialRefundUsesOriginalFrozenWalletAndCompletedTimestamp() {
        service.refundOnCompletion(50L);
        verify(wallet).refundToSettlement(50L, 2L, 3L, new BigDecimal("30"), completedAt);
    }

    @Test
    void cumulativeReturnsCannotExceedAllOriginalConsumptionPrincipal() {
        when(sources.returnedAmount(10L)).thenReturn(new BigDecimal("71"));
        assertThatThrownBy(() -> service.refundOnCompletion(50L)).isInstanceOfSatisfying(ScmBusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(BalanceErrorCode.BALANCE_REFUND_AMOUNT_EXCEEDED));
        verifyNoInteractions(wallet);
    }

    @Test
    void existingRefundIsNotCountedTwiceWhenReplayed() {
        when(sources.returnedAmount(10L)).thenReturn(new BigDecimal("100"));
        when(movements.selectBySource("ORDER_REFUND", 50L)).thenReturn(new CustomerBalanceMovementEntity());
        service.refundOnCompletion(50L);
        verify(wallet).refundToSettlement(50L, 2L, 3L, new BigDecimal("30"), completedAt);
    }

    @Test
    void anyCashRefundOnTheOrderBlocksWalletReturn() {
        when(sources.hasCashRefund(10L)).thenReturn(true);
        assertThatThrownBy(() -> service.refundOnCompletion(50L)).isInstanceOfSatisfying(ScmBusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(BalanceErrorCode.BALANCE_REFUND_PATH_CONFLICT));
        verifyNoInteractions(wallet);
    }

    @Test
    void unresolvedPaymentCannotBecomeMixedFundingAfterWalletReturn() {
        when(sources.hasPendingFunding(10L)).thenReturn(true);
        assertThatThrownBy(() -> service.refundOnCompletion(50L)).isInstanceOfSatisfying(ScmBusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(BalanceErrorCode.BALANCE_REFUND_PENDING_PAYMENT));
        verifyNoInteractions(wallet);
    }

    @Test
    void mixedAndOfflineRefundCompletionDoesNotGuessWalletAllocation() {
        var online = new FinanceOrderFundingDto(); online.setMethod("ONLINE");
        when(fundingPolicy.requireCompleteOrderFunding(10L)).thenReturn(List.of(balance(1L, "60"), online));
        service.refundOnCompletion(50L);
        when(fundingPolicy.requireCompleteOrderFunding(10L)).thenReturn(List.of());
        service.refundOnCompletion(50L);
        verifyNoInteractions(wallet);
    }

    @Test
    void manualReceiptFundingAlsoPreventsGuessingPureBalanceRefund() {
        when(fundingPolicy.hasReceiptFunding(10L)).thenReturn(true);
        service.refundOnCompletion(50L);
        verifyNoInteractions(wallet);
    }

    @Test
    void duplicateMovementCannotInflateRefundablePrincipal() {
        when(fundingPolicy.requireCompleteOrderFunding(10L)).thenReturn(List.of(balance(1L, "60"), balance(1L, "60")));
        assertThatThrownBy(() -> service.refundOnCompletion(50L)).isInstanceOf(ScmBusinessException.class);
        verifyNoInteractions(wallet);
    }

    private static FinanceOrderFundingDto balance(Long movementId, String amount) {
        var fact = new FinanceOrderFundingDto();
        fact.setMethod("BALANCE"); fact.setCustomerId(2L); fact.setSettlementCustomerId(3L);
        fact.setMovementId(movementId); fact.setMovementAmount(new BigDecimal(amount));
        return fact;
    }
}
