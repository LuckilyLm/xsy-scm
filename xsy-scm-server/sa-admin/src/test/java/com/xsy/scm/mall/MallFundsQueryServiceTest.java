package com.xsy.scm.mall;

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

import com.xsy.scm.balance.service.CustomerBalanceService;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.domain.entity.CustomerTypeEntity;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.customer.service.CustomerTypeService;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.funds.dao.MallFundsSourceDao;
import com.xsy.scm.mall.funds.domain.form.MallReceivableQueryForm;
import com.xsy.scm.mall.funds.domain.form.MallWalletMovementQueryForm;
import com.xsy.scm.mall.funds.service.MallFundsQueryService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class MallFundsQueryServiceTest {
    private final CustomerService customers = mock(CustomerService.class);
    private final CustomerTypeService customerTypes = mock(CustomerTypeService.class);
    private final CustomerBalanceService balances = mock(CustomerBalanceService.class);
    private final MallFundsSourceDao sources = mock(MallFundsSourceDao.class);
    private final MallFundsQueryService service = new MallFundsQueryService(customers, customerTypes, balances,
            sources);

    @Test
    void childAccountCannotSeeSharedWalletTotalButCanRequestOnlyItsOwnMovements() {
        RequestMallCustomer identity = identity(20L);
        CustomerEntity child = customer(20L, 10L, null);
        CustomerEntity parent = customer(10L, 10L, 1L);
        when(customers.requireTradable(20L)).thenReturn(child);
        when(customers.requireSettlementAccount(child)).thenReturn(parent);
        // 共享判定现在由 CustomerValidator.sharedSettlement 统一给出：结算主体类型码是必传参数，
        // 因此这里必须显式给出类型（非 GROUP，共享仅因「结算主体不是本人」成立）。
        when(customerTypes.require(1L)).thenReturn(enterpriseType());

        var summary = service.walletSummary(identity);
        assertThat(summary.getBalanceVisible()).isFalse();
        assertThat(summary.getAvailableBalance()).isNull();
        assertThat(summary.getHasWallet()).isNull();
        verifyNoInteractions(balances);
        verify(sources, never()).walletAccountExists(10L);

        MallWalletMovementQueryForm form = new MallWalletMovementQueryForm();
        form.setPageNum(1L);
        form.setPageSize(20L);
        when(sources.walletMovements(any(), eq(20L), eq(10L), isNull(), isNull())).thenReturn(List.of());
        service.walletMovements(identity, form);
        verify(sources).walletMovements(any(), eq(20L), eq(10L), isNull(), isNull());
    }

    @Test
    void groupSettlementAccountDoesNotExposeWholeGroupBalanceToItsPurchasingLogin() {
        RequestMallCustomer identity = identity(10L);
        CustomerEntity group = customer(10L, 10L, 1L);
        CustomerTypeEntity groupType = new CustomerTypeEntity();
        groupType.setTypeCode("GROUP");
        when(customers.requireTradable(10L)).thenReturn(group);
        when(customers.requireSettlementAccount(group)).thenReturn(group);
        when(customerTypes.require(1L)).thenReturn(groupType);

        assertThat(service.walletSummary(identity).getBalanceVisible()).isFalse();
        verifyNoInteractions(balances);
    }

    @Test
    void sharedSettlementCreditSummaryAndReceivableListRemainScopedToThePurchasingCustomer() {
        RequestMallCustomer identity = identity(20L);
        CustomerEntity child = customer(20L, 10L, null);
        CustomerEntity parent = customer(10L, 10L, 1L);
        parent.setCreditLimit(new BigDecimal("1000.0000"));
        when(customers.requireTradable(20L)).thenReturn(child);
        when(customers.requireSettlementAccount(child)).thenReturn(parent);
        when(customerTypes.require(1L)).thenReturn(enterpriseType());
        MallFundsSourceDao.CreditFacts facts = new MallFundsSourceDao.CreditFacts();
        facts.setOpenReceivableAmount(new BigDecimal("20.0000"));
        facts.setConfirmedOrderAmount(new BigDecimal("10.0000"));
        when(sources.creditFacts(20L)).thenReturn(facts);

        var summary = service.creditSummary(identity);
        assertThat(summary.getSharedSettlement()).isTrue();
        assertThat(summary.getCreditLimit()).isNull();
        assertThat(summary.getOpenReceivableAmount()).isEqualByComparingTo("20.0000");
        verify(sources).creditFacts(20L);

        MallReceivableQueryForm form = new MallReceivableQueryForm();
        form.setPageNum(1L);
        form.setPageSize(20L);
        when(sources.receivables(any(), eq(20L), isNull())).thenReturn(List.of());
        service.receivables(identity, form);
        verify(sources).receivables(any(), eq(20L), isNull());
    }

    @Test
    void missingOrOtherCustomerReceivableUsesTheSameNotFoundResult() {
        RequestMallCustomer identity = identity(20L);
        when(customers.requireTradable(20L)).thenReturn(customer(20L, 20L, null));

        assertThatThrownBy(() -> service.receivable(identity, 99L))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(MallErrorCode.FUNDS_RECEIVABLE_NOT_FOUND));
        verify(sources).receivable(20L, 99L);
    }

    private static RequestMallCustomer identity(Long customerId) {
        RequestMallCustomer identity = new RequestMallCustomer();
        identity.setCustomerId(customerId);
        return identity;
    }

    private static CustomerEntity customer(Long id, Long settlementId, Long typeId) {
        CustomerEntity customer = new CustomerEntity();
        customer.setId(id);
        customer.setSettlementCustomerId(settlementId);
        customer.setCustomerTypeId(typeId);
        return customer;
    }

    private static CustomerTypeEntity enterpriseType() {
        CustomerTypeEntity type = new CustomerTypeEntity();
        type.setTypeCode("ENTERPRISE");
        return type;
    }
}
