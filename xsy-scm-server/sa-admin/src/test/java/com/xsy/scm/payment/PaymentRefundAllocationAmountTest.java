package com.xsy.scm.payment;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.finance.service.FinanceOrderFundingPolicy;
import com.xsy.scm.finance.service.FinancePaymentService;
import com.xsy.scm.payment.dao.PaymentIntentDao;
import com.xsy.scm.payment.dao.PaymentRefundDao;
import com.xsy.scm.payment.dao.PaymentSourceDao;
import com.xsy.scm.payment.dao.PaymentTransactionDao;
import com.xsy.scm.payment.dao.RefundFundingAllocationDao;
import com.xsy.scm.payment.domain.entity.PaymentRefundEntity;
import com.xsy.scm.payment.domain.entity.PaymentIntentEntity;
import com.xsy.scm.payment.domain.dto.PaymentOrderFact;
import com.xsy.scm.payment.domain.entity.RefundFundingAllocationEntity;
import com.xsy.scm.payment.provider.ScmPaymentProvider;
import com.xsy.scm.payment.provider.ScmPaymentProviderRegistry;
import com.xsy.scm.payment.service.PaymentNumberGenerator;
import com.xsy.scm.payment.service.PaymentRefundService;
import com.xsy.scm.payment.service.RefundFundingCompletionService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

class PaymentRefundAllocationAmountTest {
    private final PaymentRefundDao refunds = mock(PaymentRefundDao.class);
    private final RefundFundingAllocationDao allocations = mock(RefundFundingAllocationDao.class);
    private final FinancePaymentService financePayments = mock(FinancePaymentService.class);
    private final PaymentIntentDao intents = mock(PaymentIntentDao.class);
    private final PaymentSourceDao sources = mock(PaymentSourceDao.class);
    private final PaymentRefundService service = new PaymentRefundService(refunds, allocations,
            mock(PaymentTransactionDao.class), intents, sources,
            financePayments, mock(FinanceOrderFundingPolicy.class), mock(PaymentNumberGenerator.class),
            mock(ScmPaymentProviderRegistry.class), mock(ScmIdempotencyService.class),
            mock(ScmDataScopeService.class), mock(RefundFundingCompletionService.class));

    @BeforeEach
    void source() {
        PaymentIntentEntity intent = new PaymentIntentEntity();
        intent.setSourceType("SALES_ORDER");
        intent.setSourceId(51L);
        when(intents.selectById(11L)).thenReturn(intent);
        when(sources.lockOrder(51L)).thenReturn(mock(PaymentOrderFact.class));
        when(refunds.updateProviderReference(31L, "PR31", "1:7")).thenReturn(1);
    }

    @Test
    void providerAmountMismatchKeepsAllocationPendingForReconciliation() {
        PaymentRefundEntity refund = refund("100.0000", "98.0000");
        RefundFundingAllocationEntity allocation = allocation("100.0000");
        when(refunds.selectById(31L)).thenReturn(refund);
        when(refunds.lockById(31L)).thenReturn(refund);
        when(allocations.selectById(41L)).thenReturn(allocation);

        service.applyOutcome(31L, ScmPaymentProvider.Outcome.SUCCEEDED, "PR31",
                new BigDecimal("98.0000"), null, null, "1:7");

        verify(refunds).markSucceeded(31L, "PR31", new BigDecimal("98.0000"), "1:7");
        verify(allocations, never()).markSucceeded(41L, 31L, "1:7");
        verifyNoInteractions(financePayments);
    }

    @Test
    void exactProviderAmountCompletesAllocation() {
        PaymentRefundEntity refund = refund("100.0000", "100.0000");
        RefundFundingAllocationEntity allocation = allocation("100.0000");
        when(refunds.selectById(31L)).thenReturn(refund);
        when(refunds.lockById(31L)).thenReturn(refund);
        when(allocations.selectById(41L)).thenReturn(allocation);

        service.applyOutcome(31L, ScmPaymentProvider.Outcome.SUCCEEDED, "PR31",
                new BigDecimal("100.0000"), null, null, "1:7");

        verify(allocations).markSucceeded(41L, 31L, "1:7");
        verifyNoInteractions(financePayments);
    }

    private static PaymentRefundEntity refund(String requested, String actual) {
        PaymentRefundEntity refund = new PaymentRefundEntity();
        refund.setId(31L);
        refund.setIntentId(11L);
        refund.setAmount(new BigDecimal(requested));
        refund.setProviderAmount(new BigDecimal(actual));
        refund.setSourceType("REFUND_FUNDING_ALLOCATION");
        refund.setSourceId(41L);
        refund.setStatus("SUCCEEDED");
        return refund;
    }

    private static RefundFundingAllocationEntity allocation(String amount) {
        RefundFundingAllocationEntity allocation = new RefundFundingAllocationEntity();
        allocation.setId(41L);
        allocation.setAllocatedAmount(new BigDecimal(amount));
        allocation.setStatus("PROCESSING");
        return allocation;
    }
}
