package com.xsy.scm.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.finance.dao.FinanceReceivableDao;
import com.xsy.scm.finance.dao.FinanceReceivableItemDao;
import com.xsy.scm.finance.domain.entity.FinanceReceivableEntity;
import com.xsy.scm.finance.domain.vo.FinanceReceivableVO;
import com.xsy.scm.finance.service.FinanceOperationLogQueryService;
import com.xsy.scm.finance.service.FinanceReceivableQueryService;
import com.xsy.scm.finance.service.FinanceWriteOffQueryService;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 独立退货扣减没有订单，详情与列表仍须遵守同一负责人范围。 */
class FinanceReceivableDetailScopeTest {
    private final FinanceReceivableDao accounts = mock(FinanceReceivableDao.class);
    private final FinanceReceivableItemDao items = mock(FinanceReceivableItemDao.class);
    private final ScmDataScopeService scopes = mock(ScmDataScopeService.class);
    private final FinanceReceivableQueryService service = new FinanceReceivableQueryService(accounts, items,
        mock(FinanceWriteOffQueryService.class), mock(FinanceOperationLogQueryService.class), scopes);

    @Test
    void independentCreditUsesCustomerOwnerAndStillRejectsOtherOwners() {
        var record = new FinanceReceivableEntity();
        record.setId(31L);
        record.setCustomerId(9L);
        record.setEntryType("RED");
        var header = new FinanceReceivableVO();
        header.setReceivableId(31L);
        when(accounts.selectActiveById(31L)).thenReturn(record);
        when(accounts.selectSellerId(31L)).thenReturn(7L);
        when(accounts.selectSummaryById(31L)).thenReturn(header);
        var scope = mock(ScmDataScopeContext.class);
        when(scopes.resolve()).thenReturn(scope);
        when(scope.getOrderSellerScope()).thenReturn(ScmValueScope.of(List.of(7L)));
        assertThat(service.detail(31L).getReceivable()).isSameAs(header);

        when(scope.getOrderSellerScope()).thenReturn(ScmValueScope.of(List.of(8L)));
        assertThatThrownBy(() -> service.detail(31L)).isInstanceOf(ScmDataScopeException.class);
    }

    @Test
    void unassignedOwnerIsNotGrantedToRestrictedAccount() {
        var record = new FinanceReceivableEntity();
        record.setId(32L);
        when(accounts.selectActiveById(32L)).thenReturn(record);
        var scope = mock(ScmDataScopeContext.class);
        when(scopes.resolve()).thenReturn(scope);
        when(scope.getOrderSellerScope()).thenReturn(ScmValueScope.of(List.of(7L)));
        assertThatThrownBy(() -> service.detail(32L)).isInstanceOf(ScmDataScopeException.class);
        verify(accounts, never()).selectSummaryById(32L);
    }
}
