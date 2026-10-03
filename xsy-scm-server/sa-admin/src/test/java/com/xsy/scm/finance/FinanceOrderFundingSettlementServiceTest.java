package com.xsy.scm.finance;

import com.xsy.scm.finance.dao.FinanceOrderFundingSourceDao;
import com.xsy.scm.finance.dao.FinanceReceiptDao;
import com.xsy.scm.finance.dao.FinanceReceivableDao;
import com.xsy.scm.finance.domain.entity.FinanceReceivableEntity;
import com.xsy.scm.finance.service.FinanceOrderFundingPolicy;
import com.xsy.scm.finance.service.FinanceOrderFundingSettlementService;
import com.xsy.scm.finance.service.FinanceOrderFundingWriteOffService;
import com.xsy.scm.finance.support.FinanceFundingAllocationResult;
import com.xsy.scm.finance.support.FinanceFundingAllocationResult.Status;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 订单资金自动分配的编排：加锁顺序、缺应收时的结论、以及既有处理结果不被覆盖。 */
class FinanceOrderFundingSettlementServiceTest {
    private final FinanceReceivableDao receivables = mock(FinanceReceivableDao.class);
    private final FinanceReceiptDao receipts = mock(FinanceReceiptDao.class);
    private final FinanceOrderFundingSourceDao sources = mock(FinanceOrderFundingSourceDao.class);
    private final FinanceOrderFundingWriteOffService writeOffs = mock(FinanceOrderFundingWriteOffService.class);
    private final FinanceOrderFundingSettlementService service = new FinanceOrderFundingSettlementService(
            receivables, receipts, sources, new FinanceOrderFundingPolicy(sources), writeOffs);

    @BeforeEach
    void funding() {
        var balance = FinanceOrderFundingPolicyTest.balance();
        var online = FinanceOrderFundingPolicyTest.online();
        online.setTransactionId(21L);
        when(sources.selectOrderFunding(10L)).thenReturn(List.of(balance, online));
        var normal = new FinanceReceivableEntity(); normal.setId(50L);
        when(receivables.selectNormalByOrder(10L)).thenReturn(normal);
    }

    @Test
    void everyFundingSourceIsLockedBeforeAnyWriteOffIsRegistered() {
        when(writeOffs.register(20L, 50L))
                .thenReturn(new FinanceFundingAllocationResult(20L, Status.APPLIED, new BigDecimal("100")));
        when(writeOffs.register(21L, 50L))
                .thenReturn(new FinanceFundingAllocationResult(21L, Status.APPLIED, new BigDecimal("98")));
        service.settleSalesOrderFunding(10L);
        // 先锁全部来源再锁目标：否则下一笔来源与人工核销会形成目标 → 来源的反向锁链
        var sequence = inOrder(sources, receipts, writeOffs);
        sequence.verify(sources).lockMovement(30L);
        sequence.verify(receipts).selectByIdForUpdate(40L);
        sequence.verify(writeOffs).register(20L, 50L);
        sequence.verify(writeOffs).register(21L, 50L);
    }

    @Test
    void missingNormalReceivableIsReportedPerFundingWithoutTouchingWriteOff() {
        when(receivables.selectNormalByOrder(10L)).thenReturn(null);
        var results = service.settleSalesOrderFunding(10L);
        assertThat(results).hasSize(2);
        assertThat(results).extracting(FinanceFundingAllocationResult::transactionId).containsExactly(20L, 21L);
        assertThat(results).allSatisfy(result -> {
            assertThat(result.status()).isEqualTo(Status.NO_RECEIVABLE);
            assertThat(result.appliedAmount()).isEqualByComparingTo("0");
        });
        verifyNoInteractions(writeOffs);
        verify(sources, never()).lockMovement(anyLong());
        verify(receipts, never()).selectByIdForUpdate(anyLong());
    }

    @Test
    void allocationsKeepTheFundingOrderAndPreserveStatusesLeftByEarlierProcessing() {
        when(writeOffs.register(20L, 50L))
                .thenReturn(new FinanceFundingAllocationResult(20L, Status.REVERSED, BigDecimal.ZERO));
        when(writeOffs.register(21L, 50L))
                .thenReturn(new FinanceFundingAllocationResult(21L, Status.APPLIED, new BigDecimal("98")));
        var results = service.settleSalesOrderFunding(10L);
        assertThat(results).extracting(FinanceFundingAllocationResult::status)
                .containsExactly(Status.REVERSED, Status.APPLIED);
        assertThat(results.get(1).appliedAmount()).isEqualByComparingTo("98");
    }
}
