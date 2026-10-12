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
import com.xsy.scm.mall.account.domain.form.MallPasswordResetForm;
import com.xsy.scm.mall.account.domain.form.MallAccountStatusForm;
import com.xsy.scm.mall.account.service.MallAccountService;
import com.xsy.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import net.lab1024.sa.base.module.support.securityprotect.service.SecurityLoginService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MallAccountServiceTest {
    private final MallCustomerAccountDao accounts = mock(MallCustomerAccountDao.class);
    private final CustomerService customers = mock(CustomerService.class);
    private final SecurityLoginService securityLogin = mock(SecurityLoginService.class);
    private final MallAccountService service = new MallAccountService(accounts, customers, securityLogin);

    /**
     * 账号写入路径会调 {@code ScmOperator.current()} 记录操作人，没有登录上下文会直接抛
     * {@code SCM mutation requires an authenticated operator}；这里提供后台操作者身份。
     */
    @BeforeEach
    void setUpOperator() {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        SmartRequestUtil.setRequestUser(employee);
    }

    @AfterEach
    void tearDownOperator() {
        SmartRequestUtil.remove();
    }

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
    void passwordResetClearsLoginFailureProtection() {
        MallCustomerAccountEntity account = new MallCustomerAccountEntity();
        account.setId(7L);
        account.setCustomerId(8L);
        when(accounts.selectByIdForUpdate(7L)).thenReturn(account);
        MallPasswordResetForm form = new MallPasswordResetForm();
        form.setAccountId(7L);
        form.setTemporaryPassword("temporary-password");

        service.resetPassword(form);

        verify(securityLogin).removeLoginFail(7L,
                net.lab1024.sa.base.common.enumeration.UserTypeEnum.MALL_CUSTOMER);
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
