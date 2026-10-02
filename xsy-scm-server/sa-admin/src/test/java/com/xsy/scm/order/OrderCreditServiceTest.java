package com.xsy.scm.order;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.finance.domain.dto.FinanceCreditExposureDto;
import com.xsy.scm.finance.service.FinanceCreditExposureQueryService;
import com.xsy.scm.order.service.OrderCreditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderCreditServiceTest {
    private final CustomerService customers = mock(CustomerService.class);
    private final FinanceCreditExposureQueryService facts = mock(FinanceCreditExposureQueryService.class);
    private final ScmDataScopeService scopes = mock(ScmDataScopeService.class);
    private final CustomerEntity settlement = new CustomerEntity();
    private final FinanceCreditExposureDto exposure = new FinanceCreditExposureDto();
    private final OrderCreditService service = new OrderCreditService(customers, facts, scopes);

    @BeforeEach void setUp() {
        var context = mock(ScmDataScopeContext.class);
        when(scopes.resolve()).thenReturn(context);
        when(context.getOrderSellerScope()).thenReturn(ScmValueScope.all());
        settlement.setId(9L);
        settlement.setCreditLimit(new BigDecimal("100"));
        when(customers.require(9L)).thenReturn(settlement);
        when(customers.lockCreditAccount(9L)).thenReturn(settlement);
        exposure.setOpenReceivableAmount(new BigDecimal("60"));
        exposure.setConfirmedOrderAmount(new BigDecimal("30"));
        exposure.setExposureVisible(true);
        when(facts.summarize(9L, ScmValueScope.all())).thenReturn(exposure);
    }

    @Test void explainsProjectedExposureAndOverdue() {
        exposure.setEarliestOverdueDate(LocalDate.of(2026,1,1));
        var result = service.checkSettlement(9L,new BigDecimal("20"));
        assertThat(result.getProjectedExposure()).isEqualByComparingTo("110");
        assertThat(result.getOverLimit()).isTrue();
        assertThat(result.getOverdue()).isTrue();
        assertThat(result.getAllowed()).isFalse();
    }

    @Test void zeroLimitAllowsAmountButNeverIgnoresOverdue() {
        settlement.setCreditLimit(BigDecimal.ZERO);
        assertThat(service.checkForSettlementConfirmation(9L, new BigDecimal("1000")).getAllowed()).isTrue();
        exposure.setEarliestOverdueDate(LocalDate.of(2026,1,1));
        var blocked = service.checkForSettlementConfirmation(9L,new BigDecimal("1000"));
        assertThat(blocked.getOverLimit()).isFalse();
        assertThat(blocked.getAllowed()).isFalse();
    }

    @Test void hiddenGroupOrdersCannotExposeAccountTotals() {
        exposure.setExposureVisible(false);
        assertThatThrownBy(() -> service.checkSettlement(9L,BigDecimal.ZERO)).isInstanceOf(ScmDataScopeException.class);
    }

    @Test void internalConfirmationStillAccountsForHiddenGroupExposure() {
        exposure.setExposureVisible(false);
        assertThat(service.checkForSettlementConfirmation(9L,new BigDecimal("20")).getAllowed()).isFalse();
    }

    @Test void negativeRequestedAmountCannotReduceExposure() {
        assertThatThrownBy(() -> service.checkSettlement(9L,BigDecimal.ONE.negate())).isInstanceOf(ScmBusinessException.class);
    }

    @Test void confirmationLocksAuthoritativeAccountBeforeRecalculatingExposure() {
        service.checkForSettlementConfirmation(9L, new BigDecimal("10"));
        var ordered = org.mockito.Mockito.inOrder(customers, facts);
        ordered.verify(customers).lockCreditAccount(9L);
        ordered.verify(facts).summarize(9L, ScmValueScope.all());
    }
}
