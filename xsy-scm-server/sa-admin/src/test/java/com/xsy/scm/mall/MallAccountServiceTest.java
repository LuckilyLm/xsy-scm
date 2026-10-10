package com.xsy.scm.mall;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.account.dao.MallCustomerAccountDao;
import com.xsy.scm.mall.account.domain.entity.MallCustomerAccountEntity;
import com.xsy.scm.mall.account.domain.form.MallAccountStatusForm;
import com.xsy.scm.mall.account.service.MallAccountService;
import com.xsy.scm.common.exception.ScmBusinessException;
import org.junit.jupiter.api.Test;

class MallAccountServiceTest {
    private final MallCustomerAccountDao accounts = mock(MallCustomerAccountDao.class);
    private final CustomerService customers = mock(CustomerService.class);
    private final MallAccountService service = new MallAccountService(accounts, customers);

    @Test
    void accountMutationRechecksCurrentCustomerScope() {
        MallCustomerAccountEntity account = new MallCustomerAccountEntity();
        account.setId(7L);
        account.setCustomerId(8L);
        when(accounts.selectByIdForUpdate(7L)).thenReturn(account);
        when(customers.requireInCurrentScope(8L)).thenThrow(new RuntimeException("out of scope"));
        MallAccountStatusForm form = new MallAccountStatusForm();
        form.setAccountId(7L);
        form.setEnabled(false);
        assertThatThrownBy(() -> service.updateStatus(form)).hasMessage("out of scope");
    }

    @Test
    void missingAccountDoesNotProbeCustomer() {
        MallAccountStatusForm form = new MallAccountStatusForm();
        form.setAccountId(99L);
        form.setEnabled(false);
        assertThatThrownBy(() -> service.updateStatus(form)).isInstanceOfSatisfying(ScmBusinessException.class,
                exception -> org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                        .isEqualTo(MallErrorCode.ACCOUNT_NOT_FOUND));
    }
}
