package com.xsy.scm.balance;

import com.xsy.scm.balance.constant.BalanceErrorCode;
import com.xsy.scm.balance.constant.ScmBalanceMovementTypeEnum;
import com.xsy.scm.balance.constant.ScmBalanceSourceTypeEnum;
import com.xsy.scm.balance.dao.CustomerBalanceAccountDao;
import com.xsy.scm.balance.dao.CustomerBalanceMovementDao;
import com.xsy.scm.balance.dao.CustomerBalanceRechargeDao;
import com.xsy.scm.balance.domain.entity.CustomerBalanceAccountEntity;
import com.xsy.scm.balance.domain.entity.CustomerBalanceMovementEntity;
import com.xsy.scm.balance.domain.entity.CustomerBalanceRechargeEntity;
import com.xsy.scm.balance.domain.form.BalanceCorrectionForm;
import com.xsy.scm.balance.service.CustomerBalanceService;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.service.CustomerService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 入账侧原语：充值 / 退款返还 / 人工更正 / 充值回调，以及只读余额不写库这条纪律。 */
class CustomerBalanceLedgerTest {
    private static final OffsetDateTime PAID_AT = OffsetDateTime.parse("2026-10-04T12:00:00+08:00");

    private final CustomerBalanceAccountDao accounts = mock(CustomerBalanceAccountDao.class);
    private final CustomerBalanceMovementDao movements = mock(CustomerBalanceMovementDao.class);
    private final CustomerBalanceRechargeDao recharges = mock(CustomerBalanceRechargeDao.class);
    private final CustomerService customers = mock(CustomerService.class);
    private final ScmIdempotencyService idempotency = mock(ScmIdempotencyService.class);
    private final CustomerBalanceService service = new CustomerBalanceService(accounts, movements, recharges,
            customers, idempotency);

    @BeforeEach
    void wallet() {
        var customer = new CustomerEntity(); customer.setId(2L); customer.setName("门店"); customer.setSellerId(9L);
        var settlement = new CustomerEntity(); settlement.setId(3L); settlement.setName("集团");
        when(customers.require(2L)).thenReturn(customer);
        when(customers.requireSettlementAccount(customer)).thenReturn(settlement);
        var account = new CustomerBalanceAccountEntity(); account.setId(4L); account.setSettlementCustomerId(3L);
        when(accounts.lockBySettlementCustomerId(3L)).thenReturn(account);
    }

    @Test
    void creditOnlyAcceptsTheSourceTypeThatPairsWithTheMovementType() {
        var amount = new BigDecimal("100");
        assertSourceInvalid(() -> service.credit(ScmBalanceMovementTypeEnum.RECHARGE, 2L, amount,
                ScmBalanceSourceTypeEnum.ORDER_REFUND, 7L, "充值"));
        assertSourceInvalid(() -> service.credit(ScmBalanceMovementTypeEnum.REFUND, 2L, amount,
                ScmBalanceSourceTypeEnum.PAYMENT_TRANSACTION, 7L, "返还"));
        assertSourceInvalid(() -> service.credit(ScmBalanceMovementTypeEnum.CONSUME, 2L, amount,
                ScmBalanceSourceTypeEnum.PAYMENT_TRANSACTION, 7L, "消费"));
        assertSourceInvalid(() -> service.credit(ScmBalanceMovementTypeEnum.RECHARGE, 2L, amount,
                ScmBalanceSourceTypeEnum.PAYMENT_TRANSACTION, null, "充值"));
        verifyNoInteractions(accounts, movements, recharges);
    }

    @Test
    void firstCreditCreatesTheWalletAccountUnderLockThenAppendsTheMovement() {
        var account = new CustomerBalanceAccountEntity(); account.setId(4L); account.setSettlementCustomerId(3L);
        when(accounts.lockBySettlementCustomerId(3L)).thenReturn(null, account);
        when(movements.nextMovementNo()).thenReturn(7L);
        when(movements.insertOnConflictDoNothing(any())).thenAnswer(invocation -> {
            CustomerBalanceMovementEntity row = invocation.getArgument(0); row.setId(5L); return 1;
        });
        CustomerBalanceMovementEntity movement;
        try (var operator = mockStatic(ScmOperator.class)) {
            operator.when(ScmOperator::current).thenReturn("1:1");
            movement = service.credit(ScmBalanceMovementTypeEnum.RECHARGE, 2L, new BigDecimal("100"),
                    ScmBalanceSourceTypeEnum.PAYMENT_TRANSACTION, 20L, "在线充值 CBR1");
        }
        var created = ArgumentCaptor.forClass(CustomerBalanceAccountEntity.class);
        verify(accounts).insertOnConflictDoNothing(created.capture());
        assertThat(created.getValue().getSettlementCustomerId()).isEqualTo(3L);
        assertThat(created.getValue().getSettlementCustomerNameSnapshot()).isEqualTo("集团");
        assertThat(created.getValue().getCreatedBy()).isEqualTo("1:1");
        var sequence = inOrder(accounts, movements);
        sequence.verify(accounts).lockBySettlementCustomerId(3L);
        sequence.verify(accounts).insertOnConflictDoNothing(any());
        sequence.verify(accounts).lockBySettlementCustomerId(3L);
        sequence.verify(movements).selectBySource("PAYMENT_TRANSACTION", 20L);
        assertThat(movement.getId()).isEqualTo(5L);
        assertThat(movement.getMovementNo()).startsWith("CBM").endsWith("000007");
        assertThat(movement.getAccountId()).isEqualTo(4L);
        assertThat(movement.getSettlementCustomerId()).isEqualTo(3L);
        assertThat(movement.getCustomerId()).isEqualTo(2L);
        assertThat(movement.getType()).isEqualTo("RECHARGE");
        assertThat(movement.getDirection()).isEqualTo("CREDIT");
        assertThat(movement.getAmount()).isEqualByComparingTo("100");
        assertThat(movement.getAmount().scale()).isEqualTo(4);
        assertThat(movement.getSourceType()).isEqualTo("PAYMENT_TRANSACTION");
        assertThat(movement.getSourceId()).isEqualTo(20L);
        assertThat(movement.getReason()).isEqualTo("在线充值 CBR1");
        assertThat(movement.getCreatedBy()).isEqualTo("1:1");
    }

    @Test
    void repeatedCallbackReturnsTheExistingCreditAndRejectsAChangedAmount() {
        var existing = movement(5L, "RECHARGE", "CREDIT", "100");
        when(movements.selectBySource("PAYMENT_TRANSACTION", 20L)).thenReturn(existing);
        assertThat(service.credit(ScmBalanceMovementTypeEnum.RECHARGE, 2L, new BigDecimal("100"),
                ScmBalanceSourceTypeEnum.PAYMENT_TRANSACTION, 20L, "在线充值")).isSameAs(existing);
        // 同一来源换了金额就是另一笔钱：宁可失败，也不悄悄按新金额再入一次账
        assertSourceInvalid(() -> service.credit(ScmBalanceMovementTypeEnum.RECHARGE, 2L, new BigDecimal("101"),
                ScmBalanceSourceTypeEnum.PAYMENT_TRANSACTION, 20L, "在线充值"));
        verify(movements, never()).insertOnConflictDoNothing(any());
        verify(movements, never()).sumSignedByAccount(anyLong(), anyString());
    }

    @Test
    void correctionAppendsASignedMovementAndCompletesTheIdempotencyClaim() {
        var form = correction("CREDIT", "30", "  对账修正  ");
        var claim = new ScmIdempotencyService.Claim(null, false);
        when(idempotency.claim("BALANCE_CORRECTION:2", "key", form)).thenReturn(claim);
        when(movements.nextMovementNo()).thenReturn(8L);
        when(movements.insertOnConflictDoNothing(any())).thenAnswer(invocation -> {
            CustomerBalanceMovementEntity row = invocation.getArgument(0); row.setId(6L); return 1;
        });
        CustomerBalanceMovementEntity movement;
        try (var operator = mockStatic(ScmOperator.class)) {
            operator.when(ScmOperator::current).thenReturn("1:1");
            movement = service.correct(form, "key");
        }
        assertThat(movement.getId()).isEqualTo(6L);
        assertThat(movement.getType()).isEqualTo("CORRECTION");
        assertThat(movement.getDirection()).isEqualTo("CREDIT");
        assertThat(movement.getReason()).isEqualTo("对账修正");
        assertThat(movement.getSourceType()).isNull();
        assertThat(movement.getSourceId()).isNull();
        verify(idempotency).complete(claim, "BALANCE_MOVEMENT", 6L, movement);
        // 增加方向不复核可用余额：「不允许负余额」只约束扣款
        verify(movements, never()).sumSignedByAccount(anyLong(), anyString());
    }

    @Test
    void correctionRejectsUnknownDirectionBadAmountAndBlankReasonBeforeClaimingIdempotency() {
        assertFailure(() -> service.correct(correction("UP", "30", "对账修正"), "key"),
                BalanceErrorCode.BALANCE_DIRECTION_INVALID);
        assertFailure(() -> service.correct(correction("CREDIT", "0", "对账修正"), "key"),
                BalanceErrorCode.BALANCE_AMOUNT_INVALID);
        assertFailure(() -> service.correct(correction("DEBIT", "30", "   "), "key"),
                BalanceErrorCode.BALANCE_REASON_REQUIRED);
        assertFailure(() -> service.correct(correction("DEBIT", "30", null), "key"),
                BalanceErrorCode.BALANCE_REASON_REQUIRED);
        verifyNoInteractions(idempotency, accounts, movements);
    }

    @Test
    void debitCorrectionRechecksAvailableBalanceInsideTheAccountLock() {
        var form = correction("DEBIT", "60", "对账修正");
        when(idempotency.claim("BALANCE_CORRECTION:2", "key", form))
                .thenReturn(new ScmIdempotencyService.Claim(null, false));
        when(movements.sumSignedByAccount(4L, "CREDIT")).thenReturn(new BigDecimal("59"));
        assertFailure(() -> service.correct(form, "key"), BalanceErrorCode.BALANCE_INSUFFICIENT);
        var sequence = inOrder(accounts, movements);
        sequence.verify(accounts).lockBySettlementCustomerId(3L);
        sequence.verify(movements).sumSignedByAccount(4L, "CREDIT");
        verify(movements, never()).insertOnConflictDoNothing(any());
        verify(idempotency, never()).complete(any(), anyString(), any(), any());
    }

    @Test
    void rechargeCreditsTheProviderAmountEvenWhenItDiffersFromTheRequest() {
        when(recharges.selectByIdForUpdate(9L)).thenReturn(recharge(9L, 2L, 3L, "100"));
        when(movements.nextMovementNo()).thenReturn(11L);
        when(movements.insertOnConflictDoNothing(any())).thenAnswer(invocation -> {
            CustomerBalanceMovementEntity row = invocation.getArgument(0); row.setId(7L); return 1;
        });
        try (var operator = mockStatic(ScmOperator.class)) {
            operator.when(ScmOperator::current).thenReturn("1:1");
            service.rechargeFromPayment(9L, 2L, new BigDecimal("98"), 20L, PAID_AT);
        }
        var row = ArgumentCaptor.forClass(CustomerBalanceMovementEntity.class);
        verify(movements).insertOnConflictDoNothing(row.capture());
        // 钱包进多少等于公司真收多少：申请 100 而实收 98 时记 98，差异留给对账
        assertThat(row.getValue().getAmount()).isEqualByComparingTo("98");
        assertThat(row.getValue().getOccurredAt()).isEqualTo(PAID_AT);
        assertThat(row.getValue().getType()).isEqualTo("RECHARGE");
        assertThat(row.getValue().getDirection()).isEqualTo("CREDIT");
        assertThat(row.getValue().getSourceType()).isEqualTo("PAYMENT_TRANSACTION");
        assertThat(row.getValue().getSourceId()).isEqualTo(20L);
        assertThat(row.getValue().getReason()).isEqualTo("在线充值 CBR20261004000009");
    }

    @Test
    void rechargeRejectsMissingForeignOrDetachedRechargeFacts() {
        assertSourceInvalid(() -> service.rechargeFromPayment(null, 2L, new BigDecimal("100"), 20L, PAID_AT));
        assertSourceInvalid(() -> service.rechargeFromPayment(9L, 2L, new BigDecimal("100"), null, PAID_AT));
        when(recharges.selectByIdForUpdate(404L)).thenReturn(null);
        assertFailure(() -> service.rechargeFromPayment(404L, 2L, new BigDecimal("100"), 20L, PAID_AT),
                BalanceErrorCode.BALANCE_RECHARGE_NOT_FOUND);
        // 充值的业务客户不是发起支付的客户：来源身份已经分叉
        when(recharges.selectByIdForUpdate(9L)).thenReturn(recharge(9L, 999L, 3L, "100"));
        assertSourceInvalid(() -> service.rechargeFromPayment(9L, 2L, new BigDecimal("100"), 20L, PAID_AT));
        // 充值事实挂的钱包与当前解析出的结算主体不一致
        when(recharges.selectByIdForUpdate(9L)).thenReturn(recharge(9L, 2L, 999L, "100"));
        assertSourceInvalid(() -> service.rechargeFromPayment(9L, 2L, new BigDecimal("100"), 20L, PAID_AT));
        assertSourceInvalid(() -> service.rechargeFromPayment(9L, 2L, new BigDecimal("100"), 20L, null));
        verify(movements, never()).insertOnConflictDoNothing(any());
    }

    @Test
    void movementAmountMustBePositiveAndCarryAtMostFourDecimals() {
        for (BigDecimal amount : Arrays.asList(null, BigDecimal.ZERO, new BigDecimal("-100"),
                new BigDecimal("100.00001"))) {
            assertFailure(() -> service.credit(ScmBalanceMovementTypeEnum.RECHARGE, 2L, amount,
                    ScmBalanceSourceTypeEnum.PAYMENT_TRANSACTION, 20L, "在线充值"),
                    BalanceErrorCode.BALANCE_AMOUNT_INVALID);
        }
        verifyNoInteractions(accounts, movements);
    }

    @Test
    void readingABalanceNeverWritesAndRoundsToTheLedgerScale() {
        when(accounts.selectBySettlementCustomerId(3L)).thenReturn(null);
        assertThat(service.balanceOfSettlement(3L)).isEqualByComparingTo("0");
        assertThat(service.balanceOfSettlement(3L).scale()).isEqualTo(4);
        var account = new CustomerBalanceAccountEntity(); account.setId(4L); account.setSettlementCustomerId(3L);
        when(accounts.selectBySettlementCustomerId(3L)).thenReturn(account);
        when(movements.sumSignedByAccount(4L, "CREDIT")).thenReturn(null);
        assertThat(service.balanceOf(2L)).isEqualByComparingTo("0");
        when(movements.sumSignedByAccount(4L, "CREDIT")).thenReturn(new BigDecimal("12.345678"));
        assertThat(service.balanceOf(2L)).isEqualByComparingTo("12.3457");
        verify(accounts, never()).insertOnConflictDoNothing(any());
        verify(accounts, never()).lockBySettlementCustomerId(anyLong());
    }

    private void assertSourceInvalid(Executable executable) {
        assertFailure(executable, BalanceErrorCode.BALANCE_SOURCE_INVALID);
    }

    private static void assertFailure(Executable executable, BalanceErrorCode expected) {
        assertThatThrownBy(executable::execute).isInstanceOfSatisfying(ScmBusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    private static BalanceCorrectionForm correction(String direction, String amount, String reason) {
        var form = new BalanceCorrectionForm();
        form.setCustomerId(2L); form.setDirection(direction); form.setAmount(new BigDecimal(amount));
        form.setReason(reason);
        return form;
    }

    private static CustomerBalanceRechargeEntity recharge(Long id, Long customerId, Long settlementCustomerId,
            String amount) {
        var row = new CustomerBalanceRechargeEntity();
        row.setId(id); row.setRechargeNo("CBR20261004000009"); row.setCustomerId(customerId);
        row.setSettlementCustomerId(settlementCustomerId); row.setAmount(new BigDecimal(amount));
        return row;
    }

    private static CustomerBalanceMovementEntity movement(Long id, String type, String direction, String amount) {
        var row = new CustomerBalanceMovementEntity();
        row.setId(id); row.setAccountId(4L); row.setSettlementCustomerId(3L); row.setCustomerId(2L);
        row.setType(type); row.setDirection(direction); row.setAmount(new BigDecimal(amount));
        row.setOccurredAt(PAID_AT);
        return row;
    }
}
