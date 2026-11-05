package com.xsy.scm.finance;

import com.xsy.scm.common.domain.entity.ScmIdempotencyRecordEntity;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.finance.dao.FinanceCounterpartySourceDao;
import com.xsy.scm.finance.dao.FinanceCustomerExpenseDao;
import com.xsy.scm.finance.domain.form.FinanceCustomerExpenseForm;
import com.xsy.scm.finance.domain.vo.FinanceCustomerExpenseVO;
import com.xsy.scm.finance.service.FinanceCustomerExpenseService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinanceCustomerExpenseReplayTest {
    @Test
    void replayRechecksBothCustomerScopeAndCostVisibility() {
        var dao = mock(FinanceCustomerExpenseDao.class);
        var scopes = mock(ScmDataScopeService.class);
        var context = mock(ScmDataScopeContext.class);
        var idempotency = mock(ScmIdempotencyService.class);
        var claim = new ScmIdempotencyService.Claim(new ScmIdempotencyRecordEntity(), true);
        when(idempotency.claim(anyString(), anyString(), any())).thenReturn(claim);
        when(idempotency.replay(eq(claim), eq(Long[].class))).thenReturn(new Long[]{10L});
        var stored = new FinanceCustomerExpenseVO();
        stored.setSellerId(99L);
        when(dao.find(10L)).thenReturn(stored);
        when(scopes.resolve()).thenReturn(context);
        when(context.isCostVisible()).thenReturn(true);
        when(context.getCustomerSellerScope()).thenReturn(ScmValueScope.of(List.of(2L)));
        var service = new FinanceCustomerExpenseService(dao, mock(FinanceCounterpartySourceDao.class), scopes, idempotency);
        var form = new FinanceCustomerExpenseForm.BatchAdd();
        form.setExpenseMonth(LocalDate.of(2026, 11, 1));
        assertThatThrownBy(() -> service.addBatch(form, "old-key")).isInstanceOf(ScmDataScopeException.class);
        when(context.getCustomerSellerScope()).thenReturn(ScmValueScope.all());
        when(context.isCostVisible()).thenReturn(false);
        assertThatThrownBy(() -> service.addBatch(form, "old-key")).isInstanceOf(ScmDataScopeException.class);
        verify(dao, never()).insert(any());
    }
}
