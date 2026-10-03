package com.xsy.scm.balance;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.balance.dao.CustomerBalanceAccountDao;
import com.xsy.scm.balance.dao.CustomerBalanceMovementDao;
import com.xsy.scm.balance.domain.entity.CustomerBalanceAccountEntity;
import com.xsy.scm.balance.domain.form.BalanceMovementQueryForm;
import com.xsy.scm.balance.domain.form.BalanceQueryForm;
import com.xsy.scm.balance.domain.vo.BalanceMovementVO;
import com.xsy.scm.balance.service.CustomerBalanceQueryService;
import com.xsy.scm.balance.service.CustomerBalanceService;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.service.CustomerService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 余额只读查询：钱包键由服务端解析，范围失败关闭。 */
class CustomerBalanceQueryServiceTest {
    private final CustomerBalanceAccountDao accounts = mock(CustomerBalanceAccountDao.class);
    private final CustomerBalanceMovementDao movements = mock(CustomerBalanceMovementDao.class);
    private final CustomerBalanceService balances = mock(CustomerBalanceService.class);
    private final CustomerService customers = mock(CustomerService.class);
    private final ScmDataScopeService scopes = mock(ScmDataScopeService.class);
    private final CustomerBalanceQueryService service = new CustomerBalanceQueryService(accounts, movements,
            balances, customers, scopes);
    private final ScmDataScopeContext scope = mock(ScmDataScopeContext.class);
    private final BalanceQueryForm form = new BalanceQueryForm();

    @BeforeEach
    void wallet() {
        form.setCustomerId(2L);
        var customer = new CustomerEntity(); customer.setId(2L); customer.setName("门店");
        var settlement = new CustomerEntity();
        settlement.setId(3L); settlement.setName("集团"); settlement.setSellerId(9L);
        when(customers.require(2L)).thenReturn(customer);
        when(balances.settlementCustomerOf(2L)).thenReturn(settlement);
        when(scopes.resolve()).thenReturn(scope);
        when(scope.getCustomerSellerScope()).thenReturn(ScmValueScope.all());
    }

    @Test
    void overviewResolvesTheWalletFromTheSettlementCustomerNotTheSubmittedId() {
        var account = new CustomerBalanceAccountEntity(); account.setId(4L); account.setSettlementCustomerId(3L);
        when(accounts.selectBySettlementCustomerId(3L)).thenReturn(account);
        when(balances.balanceOfSettlement(3L)).thenReturn(new BigDecimal("88.0000"));
        var vo = service.balanceView(form);
        assertThat(vo.getCustomerId()).isEqualTo(2L);
        assertThat(vo.getCustomerName()).isEqualTo("门店");
        assertThat(vo.getSettlementCustomerId()).isEqualTo(3L);
        assertThat(vo.getSettlementCustomerName()).isEqualTo("集团");
        assertThat(vo.getAccountId()).isEqualTo(4L);
        assertThat(vo.getAvailableBalance()).isEqualByComparingTo("88");
        // 钱包键是结算主体：拿提交上来的业务客户 id 去取账户就会读到别人的钱包
        verify(accounts, never()).selectBySettlementCustomerId(2L);
        // 结算主体已经解析过，不再走 balanceOf 把客户关系解析第二遍
        verify(balances, never()).balanceOf(anyLong());
    }

    @Test
    void overviewWithoutAWalletReportsZeroAndNoAccountId() {
        when(accounts.selectBySettlementCustomerId(3L)).thenReturn(null);
        var vo = service.balanceView(form);
        assertThat(vo.getAccountId()).isNull();
        assertThat(vo.getAvailableBalance()).isEqualByComparingTo("0");
        verify(balances, never()).balanceOfSettlement(anyLong());
        verify(accounts, never()).insertOnConflictDoNothing(any());
    }

    @Test
    void overviewIsRejectedWhenTheWalletOwnerIsOutsideTheDataScope() {
        when(scope.getCustomerSellerScope()).thenReturn(ScmValueScope.of(List.of(999L)));
        assertThatThrownBy(() -> service.balanceView(form)).isInstanceOf(ScmDataScopeException.class);
        verify(accounts, never()).selectBySettlementCustomerId(anyLong());
        verify(balances, never()).balanceOfSettlement(anyLong());
    }

    @Test
    void movementPageShortCircuitsToAnEmptyPageWhenNoCustomerIsAuthorized() {
        when(scope.getCustomerSellerScope()).thenReturn(ScmValueScope.none());
        var page = service.movementPage(pageForm());
        assertThat(page.getTotal()).isZero();
        assertThat(page.getList()).isEmpty();
        assertThat(page.getEmptyFlag()).isTrue();
        verifyNoInteractions(movements);
    }

    @Test
    void movementPageHandsTheResolvedScopeToSqlInsteadOfFilteringInMemory() {
        var query = pageForm();
        var row = new BalanceMovementVO(); row.setId(5L);
        when(movements.queryPage(any(), eq(query), eq(scope))).thenReturn(List.of(row));
        assertThat(service.movementPage(query).getList()).containsExactly(row);
        verify(movements).queryPage(any(Page.class), eq(query), eq(scope));
    }

    private static BalanceMovementQueryForm pageForm() {
        var query = new BalanceMovementQueryForm();
        query.setPageNum(1L); query.setPageSize(10L);
        return query;
    }
}
