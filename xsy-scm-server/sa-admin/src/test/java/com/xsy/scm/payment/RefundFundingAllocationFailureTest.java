package com.xsy.scm.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.payment.dao.RefundFundingAllocationDao;
import com.xsy.scm.payment.domain.dto.PaymentOrderRefundFact;
import com.xsy.scm.payment.domain.entity.RefundFundingAllocationEntity;
import com.xsy.scm.payment.service.RefundFundingAllocationService;
import com.xsy.scm.payment.support.RefundBalanceSink;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class RefundFundingAllocationFailureTest {

    @Test
    void longSourceFailureIsBoundedBeforeWritingFailureStatus() {
        RefundFundingAllocationDao allocations = mock(RefundFundingAllocationDao.class);
        RefundBalanceSink balanceSink = mock(RefundBalanceSink.class);
        RefundFundingAllocationService service = new RefundFundingAllocationService(null, null, allocations, null,
                null, balanceSink, null, null, null, null);
        RefundFundingAllocationEntity row = new RefundFundingAllocationEntity();
        row.setId(42L);
        row.setStatus("PENDING");
        row.setSourceMethod("BALANCE");
        row.setSettlementCustomerId(9L);
        row.setAllocatedAmount(new BigDecimal("1.0000"));
        PaymentOrderRefundFact refund = new PaymentOrderRefundFact(7L, "R-7", 8L, 9L, null, null, null);
        String fullMessage = "failure:" + "x".repeat(700);
        when(balanceSink.refundAllocation(anyLong(), anyLong(), anyLong(), any(), any()))
                .thenThrow(new IllegalStateException(fullMessage));

        try (var operator = mockStatic(ScmOperator.class)) {
            operator.when(ScmOperator::current).thenReturn("1:1");
            ReflectionTestUtils.invokeMethod(service, "executeRows", refund, List.of(row), null);
        }

        ArgumentCaptor<String> failureMessage = ArgumentCaptor.forClass(String.class);
        verify(allocations).markFailed(eq(42L), eq("IllegalStateException"), failureMessage.capture(), eq("1:1"));
        assertThat(failureMessage.getValue()).hasSize(500).startsWith("failure:").endsWith("...");
    }
}
