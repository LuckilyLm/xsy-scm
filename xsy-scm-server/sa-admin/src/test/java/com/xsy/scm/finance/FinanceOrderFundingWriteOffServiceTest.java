package com.xsy.scm.finance;

import com.xsy.scm.balance.domain.entity.CustomerBalanceMovementEntity;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.finance.constant.FinanceConstant;
import com.xsy.scm.finance.constant.FinanceErrorCode;
import com.xsy.scm.finance.constant.ScmFinanceBusinessTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceOperationTypeEnum;
import com.xsy.scm.finance.dao.FinanceOrderFundingSourceDao;
import com.xsy.scm.finance.dao.FinanceReceiptDao;
import com.xsy.scm.finance.dao.FinanceReceivableDao;
import com.xsy.scm.finance.dao.FinanceWriteOffDao;
import com.xsy.scm.finance.domain.dto.FinanceReceivableTargetDto;
import com.xsy.scm.finance.domain.entity.FinanceReceiptEntity;
import com.xsy.scm.finance.domain.entity.FinanceReceivableEntity;
import com.xsy.scm.finance.domain.entity.FinanceWriteOffEntity;
import com.xsy.scm.finance.service.FinanceOrderFundingPolicy;
import com.xsy.scm.finance.service.FinanceOrderFundingWriteOffService;
import com.xsy.scm.finance.support.FinanceFundingAllocationResult.Status;
import com.xsy.scm.finance.support.FinanceOperationLogRecorder;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FinanceOrderFundingWriteOffServiceTest {
    private final FinanceOrderFundingSourceDao sources = mock(FinanceOrderFundingSourceDao.class);
    private final FinanceReceivableDao receivables = mock(FinanceReceivableDao.class);
    private final FinanceReceiptDao receipts = mock(FinanceReceiptDao.class);
    private final FinanceWriteOffDao writeOffs = mock(FinanceWriteOffDao.class);
    private final FinanceOperationLogRecorder logs = mock(FinanceOperationLogRecorder.class);
    private final FinanceOrderFundingWriteOffService service = new FinanceOrderFundingWriteOffService(sources,
            new FinanceOrderFundingPolicy(sources), receipts, receivables, writeOffs, logs);

    @BeforeEach
    void target() {
        var target = new FinanceReceivableEntity();
        target.setId(50L); target.setOrderId(10L); target.setCustomerId(2L); target.setSettlementCustomerId(3L);
        target.setAmount(new BigDecimal("70")); target.setReceivableNo("AR50");
        when(receivables.selectById(50L)).thenReturn(target);
        when(receivables.selectNormalTargetForUpdate(50L)).thenReturn(new FinanceReceivableTargetDto());
        when(receivables.selectRedAmount(50L)).thenReturn(new BigDecimal("30"));
        when(writeOffs.selectTargetWrittenOffAmount("RECEIVABLE", 50L)).thenReturn(BigDecimal.ZERO);
    }

    @Test
    void fullBalanceConsumptionRemainsAppliedWhenDeliveryAndRedReduceDebt() {
        balanceSource();
        when(writeOffs.insertBalanceOnConflictDoNothing(any())).thenReturn(1);
        var result = service.register(20L, 50L);
        assertThat(result.status()).isEqualTo(Status.APPLIED);
        assertThat(result.appliedAmount()).isEqualByComparingTo("100");
        var row = ArgumentCaptor.forClass(FinanceWriteOffEntity.class);
        verify(writeOffs).insertBalanceOnConflictDoNothing(row.capture());
        assertThat(row.getValue().getSourceType()).isEqualTo("BALANCE_MOVEMENT");
        assertThat(row.getValue().getAmount()).isEqualByComparingTo("100");
        verifyNoInteractions(receipts);
    }

    @Test
    void reversedBalanceAllocationIsNotRestoredOnReplay() {
        balanceSource();
        var existing = new FinanceWriteOffEntity();
        existing.setId(60L); existing.setTargetType("RECEIVABLE"); existing.setTargetId(50L);
        existing.setAmount(new BigDecimal("100"));
        when(writeOffs.selectNormalAllocation("BALANCE_MOVEMENT", 30L, null)).thenReturn(existing);
        when(writeOffs.hasReversal(60L)).thenReturn(true);
        assertThat(service.register(20L, 50L).status()).isEqualTo(Status.REVERSED);
        verify(writeOffs, never()).insertBalanceOnConflictDoNothing(any());
        verifyNoInteractions(logs);
    }

    @Test
    void onlineAllocationUsesOnlyUnallocatedActualReceiptAmount() {
        onlineSource();
        when(writeOffs.selectSourceUsedAmount("RECEIPT", 40L)).thenReturn(new BigDecimal("30"));
        when(writeOffs.insert(any(FinanceWriteOffEntity.class))).thenReturn(1);
        var result = service.register(20L, 50L);
        assertThat(result.status()).isEqualTo(Status.PARTIALLY_AVAILABLE);
        assertThat(result.appliedAmount()).isEqualByComparingTo("68");
    }

    @Test
    void manualPartialAllocationToSameTargetIsPreserved() {
        onlineSource();
        var existing = new FinanceWriteOffEntity();
        existing.setId(60L); existing.setAmount(new BigDecimal("10"));
        when(writeOffs.selectNormalAllocation("RECEIPT", 40L, 50L)).thenReturn(existing);
        assertThat(service.register(20L, 50L).status()).isEqualTo(Status.ALREADY_ALLOCATED);
        verify(writeOffs, never()).insert(any(FinanceWriteOffEntity.class));
        verifyNoInteractions(logs);
    }

    @Test
    void exhaustedBalanceSourceReportsNoAvailableFunds() {
        balanceSource();
        when(writeOffs.selectSourceUsedAmount("BALANCE_MOVEMENT", 30L)).thenReturn(new BigDecimal("100"));
        var result = service.register(20L, 50L);
        assertThat(result.status()).isEqualTo(Status.NO_AVAILABLE_FUNDS);
        assertThat(result.appliedAmount()).isEqualByComparingTo("0");
        verify(writeOffs, never()).insertBalanceOnConflictDoNothing(any());
        verifyNoInteractions(logs);
    }

    @Test
    void partiallyUsedBalanceSourceIsRejectedBecauseBalanceAllocationIsAllOrNothing() {
        balanceSource();
        when(writeOffs.selectSourceUsedAmount("BALANCE_MOVEMENT", 30L)).thenReturn(new BigDecimal("40"));
        assertInvalid(() -> service.register(20L, 50L));
        verify(writeOffs, never()).insertBalanceOnConflictDoNothing(any());
    }

    @Test
    void balanceMovementMustExistAndBelongToTheFundingIntent() {
        balanceSource();
        when(sources.lockMovement(30L)).thenReturn(null);
        assertInvalid(() -> service.register(20L, 50L));
        var foreign = new CustomerBalanceMovementEntity();
        foreign.setSourceId(999L); foreign.setAmount(new BigDecimal("100"));
        when(sources.lockMovement(30L)).thenReturn(foreign);
        assertInvalid(() -> service.register(20L, 50L));
        verify(writeOffs, never()).insertBalanceOnConflictDoNothing(any());
    }

    @Test
    void rechargeReceiptCannotBeAllocatedToAnOrderReceivable() {
        onlineSource();
        when(sources.isRechargeReceipt(40L)).thenReturn(true);
        assertInvalid(() -> service.register(20L, 50L));
        verify(writeOffs, never()).insert(any(FinanceWriteOffEntity.class));
    }

    @Test
    void appliedWriteOffLogsTheReceivableBalanceBeforeAndAfter() {
        balanceSource();
        when(writeOffs.insertBalanceOnConflictDoNothing(any())).thenAnswer(invocation -> {
            FinanceWriteOffEntity row = invocation.getArgument(0); row.setId(60L); return 1;
        });
        service.register(20L, 50L);
        var before = ArgumentCaptor.forClass(Object.class);
        var after = ArgumentCaptor.forClass(Object.class);
        verify(logs).record(eq(ScmFinanceBusinessTypeEnum.WRITE_OFF), eq(60L),
                eq(ScmFinanceOperationTypeEnum.WRITE_OFF), isNull(), before.capture(), after.capture(),
                eq(FinanceConstant.ORDER_FUNDING_OPERATOR));
        // 净额 40 = 应收 70 − 红冲 30；核销 100 因此超额 60 —— 超额只在读侧表达，不改原债权
        assertThat(snapshot(before.getValue())).containsEntry("targetNo", "AR50").containsEntry("netAmount", "40")
                .containsEntry("writtenOffAmount", "0").containsEntry("openAmount", "40")
                .containsEntry("overAppliedAmount", "0");
        assertThat(snapshot(after.getValue())).containsEntry("netAmount", "40")
                .containsEntry("writtenOffAmount", "100").containsEntry("openAmount", "0")
                .containsEntry("overAppliedAmount", "60");
    }

    @Test
    void concurrentInsertConflictReplaysTheExistingAllocation() {
        balanceSource();
        when(writeOffs.insertBalanceOnConflictDoNothing(any())).thenReturn(0);
        var existing = new FinanceWriteOffEntity();
        existing.setId(60L); existing.setTargetType("RECEIVABLE"); existing.setTargetId(50L);
        existing.setAmount(new BigDecimal("100"));
        // 首次查重为空才会走到插入；冲突后回读才拿到并发写入的那条（两次入参完全相同）
        when(writeOffs.selectNormalAllocation("BALANCE_MOVEMENT", 30L, null)).thenReturn(null, existing);
        when(writeOffs.hasReversal(60L)).thenReturn(false);
        assertThat(service.register(20L, 50L).status()).isEqualTo(Status.ALREADY_ALLOCATED);
        verifyNoInteractions(logs);
    }

    @Test
    void insertConflictAgainstADifferentAllocationFailsInsteadOfPassingSilently() {
        balanceSource();
        when(writeOffs.insertBalanceOnConflictDoNothing(any())).thenReturn(0);
        var other = new FinanceWriteOffEntity();
        other.setId(61L); other.setTargetType("RECEIVABLE"); other.setTargetId(77L);
        other.setAmount(new BigDecimal("100"));
        when(writeOffs.selectNormalAllocation("BALANCE_MOVEMENT", 30L, null)).thenReturn(null, other);
        assertInvalid(() -> service.register(20L, 50L));
        verifyNoInteractions(logs);
    }

    private static void assertInvalid(Executable executable) {
        assertThatThrownBy(executable::execute).isInstanceOfSatisfying(ScmBusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(FinanceErrorCode.ORDER_FUNDING_INVALID));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> snapshot(Object captured) {
        return (Map<String, Object>) captured;
    }

    private void balanceSource() {
        when(sources.selectTransaction(20L)).thenReturn(FinanceOrderFundingPolicyTest.balance());
        var movement = new CustomerBalanceMovementEntity();
        movement.setSourceId(1L); movement.setAmount(new BigDecimal("100"));
        when(sources.lockMovement(30L)).thenReturn(movement);
        when(writeOffs.selectSourceUsedAmount("BALANCE_MOVEMENT", 30L)).thenReturn(BigDecimal.ZERO);
    }

    private void onlineSource() {
        var fact = FinanceOrderFundingPolicyTest.online(); fact.setProviderAmount(new BigDecimal("98")); fact.setReceiptAmount(new BigDecimal("98"));
        when(sources.selectTransaction(20L)).thenReturn(fact);
        var receipt = new FinanceReceiptEntity();
        receipt.setCustomerId(2L); receipt.setSettlementCustomerId(3L); receipt.setEntryType("NORMAL");
        receipt.setAmount(new BigDecimal("98"));
        when(receipts.selectByIdForUpdate(40L)).thenReturn(receipt);
        when(receipts.selectReversedAmount(40L)).thenReturn(BigDecimal.ZERO);
    }
}
