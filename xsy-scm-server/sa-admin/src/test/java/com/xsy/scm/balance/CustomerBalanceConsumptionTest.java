package com.xsy.scm.balance;

import com.xsy.scm.balance.dao.CustomerBalanceAccountDao;
import com.xsy.scm.balance.dao.CustomerBalanceMovementDao;
import com.xsy.scm.balance.dao.CustomerBalanceRechargeDao;
import com.xsy.scm.balance.domain.entity.CustomerBalanceAccountEntity;
import com.xsy.scm.balance.domain.entity.CustomerBalanceMovementEntity;
import com.xsy.scm.balance.service.CustomerBalanceService;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.service.CustomerService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerBalanceConsumptionTest {
    private final CustomerBalanceAccountDao accounts = mock(CustomerBalanceAccountDao.class);
    private final CustomerBalanceMovementDao movements = mock(CustomerBalanceMovementDao.class);
    private final CustomerService customers = mock(CustomerService.class);
    private final CustomerBalanceService service = new CustomerBalanceService(accounts, movements,
            mock(CustomerBalanceRechargeDao.class), customers, mock(ScmIdempotencyService.class));

    @BeforeEach
    void wallet() {
        var customer = new CustomerEntity(); customer.setId(2L);
        var settlement = new CustomerEntity(); settlement.setId(3L); settlement.setName("集团");
        when(customers.require(2L)).thenReturn(customer);
        when(customers.requireSettlementAccount(customer)).thenReturn(settlement);
        var account = new CustomerBalanceAccountEntity(); account.setId(4L); account.setSettlementCustomerId(3L);
        when(accounts.lockBySettlementCustomerId(3L)).thenReturn(account);
    }

    @Test
    void debitChecksAvailableBalanceAfterAccountLockAndUsesPaymentSource() {
        when(movements.sumSignedByAccount(4L, "CREDIT")).thenReturn(new BigDecimal("200"));
        when(movements.insertOnConflictDoNothing(any())).thenAnswer(invocation -> {
            CustomerBalanceMovementEntity row = invocation.getArgument(0); row.setId(5L); return 1;
        });
        try (var operator = mockStatic(ScmOperator.class)) {
            operator.when(ScmOperator::current).thenReturn("1:1");
            var result = service.consumeForPayment(1L, 2L, 3L, new BigDecimal("60"));
            var sequence = inOrder(accounts, movements);
            sequence.verify(accounts).lockBySettlementCustomerId(3L);
            sequence.verify(movements).selectBySource("PAYMENT_INTENT", 1L);
            sequence.verify(movements).sumSignedByAccount(4L, "CREDIT");
            var row = ArgumentCaptor.forClass(CustomerBalanceMovementEntity.class);
            verify(movements).insertOnConflictDoNothing(row.capture());
            assertThat(row.getValue().getSourceId()).isEqualTo(1L);
            assertThat(row.getValue().getSourceType()).isEqualTo("PAYMENT_INTENT");
            assertThat(result.occurredAt()).isEqualTo(row.getValue().getOccurredAt());
        }
    }

    @Test
    void replayDoesNotNeedRemainingBalanceAndRejectsChangedAmount() {
        var existing = new CustomerBalanceMovementEntity();
        existing.setId(5L); existing.setAccountId(4L); existing.setSettlementCustomerId(3L); existing.setCustomerId(2L);
        existing.setType("CONSUME"); existing.setDirection("DEBIT"); existing.setAmount(new BigDecimal("60"));
        existing.setOccurredAt(OffsetDateTime.now());
        when(movements.selectBySource("PAYMENT_INTENT", 1L)).thenReturn(existing);
        assertThat(service.consumeForPayment(1L, 2L, 3L, new BigDecimal("60")).movementId()).isEqualTo(5L);
        verify(movements, never()).sumSignedByAccount(4L, "CREDIT");
        assertThatThrownBy(() -> service.consumeForPayment(1L, 2L, 3L, new BigDecimal("61")))
                .isInstanceOf(ScmBusinessException.class);
        verify(movements, never()).insertOnConflictDoNothing(any());
    }

    @Test
    void refundReturnsToFrozenWalletWithoutResolvingCurrentCustomerGroup() {
        when(movements.insertOnConflictDoNothing(any())).thenAnswer(invocation -> {
            CustomerBalanceMovementEntity row = invocation.getArgument(0); row.setId(8L); return 1;
        });
        var at = OffsetDateTime.parse("2026-10-04T15:00:00+08:00");
        try (var operator = mockStatic(ScmOperator.class)) {
            operator.when(ScmOperator::current).thenReturn("1:1");
            var returned = service.refundToSettlement(50L, 2L, 3L, new BigDecimal("30"), at);
            assertThat(returned.getSettlementCustomerId()).isEqualTo(3L);
            assertThat(returned.getSourceType()).isEqualTo("ORDER_REFUND");
            assertThat(returned.getDirection()).isEqualTo("CREDIT");
            assertThat(returned.getOccurredAt()).isEqualTo(at);
        }
        verify(customers, never()).require(2L);
    }

    @Test
    void insufficientBalanceAndChangedSettlementCannotAppendDebit() {
        when(movements.sumSignedByAccount(4L, "CREDIT")).thenReturn(new BigDecimal("59"));
        assertThatThrownBy(() -> service.consumeForPayment(1L, 2L, 3L, new BigDecimal("60")))
                .isInstanceOf(ScmBusinessException.class);
        assertThatThrownBy(() -> service.consumeForPayment(1L, 2L, 999L, new BigDecimal("60")))
                .isInstanceOf(ScmBusinessException.class);
        verify(movements, never()).insertOnConflictDoNothing(any());
    }
}
