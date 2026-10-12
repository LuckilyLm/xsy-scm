package com.xsy.scm.mall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.account.dao.MallCustomerAccountDao;
import com.xsy.scm.mall.account.domain.entity.MallCustomerAccountEntity;
import com.xsy.scm.mall.account.domain.form.MallLoginForm;
import com.xsy.scm.mall.account.domain.form.MallPasswordChangeForm;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.account.service.MallAuthService;
import jakarta.servlet.http.HttpServletRequest;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.module.support.securityprotect.domain.LoginFailEntity;
import net.lab1024.sa.base.module.support.securityprotect.service.SecurityLoginService;
import net.lab1024.sa.base.module.support.securityprotect.service.SecurityPasswordService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class MallAuthServiceTest {
    private final MallCustomerAccountDao accounts = mock(MallCustomerAccountDao.class);
    private final CustomerService customers = mock(CustomerService.class);
    private final SecurityLoginService securityLogin = mock(SecurityLoginService.class);
    private final MallAuthService service = new MallAuthService(accounts, customers, securityLogin);

    @Test
    void staleCredentialVersionCannotBuildRequestIdentity() {
        when(accounts.selectById(7L)).thenReturn(account(2));

        RequestMallCustomer result = service.requestCustomer(MallAuthService.loginId(7L, 1), request());

        assertThat(result).isNull();
        verify(customers, never()).requireTradable(9L);
    }

    @Test
    void currentCredentialVersionIsCarriedIntoRequestIdentity() {
        when(accounts.selectById(7L)).thenReturn(account(2));
        CustomerEntity customer = new CustomerEntity();
        customer.setId(9L);
        customer.setName("测试客户");
        when(customers.requireTradable(9L)).thenReturn(customer);

        RequestMallCustomer result = service.requestCustomer(MallAuthService.loginId(7L, 2), request());

        assertThat(result).isNotNull();
        assertThat(result.getAccountId()).isEqualTo(7L);
        assertThat(result.getSessionVersion()).isEqualTo(2);
    }

    @Test
    void staleSessionCannotChangePasswordAfterAnotherCredentialMutation() {
        MallCustomerAccountEntity account = account(2);
        account.setPasswordHash(SecurityPasswordService.getEncryptPwd("old-password"));
        when(accounts.selectByIdForUpdate(7L)).thenReturn(account);
        RequestMallCustomer requestCustomer = new RequestMallCustomer();
        requestCustomer.setAccountId(7L);
        requestCustomer.setSessionVersion(1);
        MallPasswordChangeForm form = new MallPasswordChangeForm();
        form.setOldPassword("old-password");
        form.setNewPassword("new-password");

        assertThatThrownBy(() -> service.changePassword(requestCustomer, form))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(MallErrorCode.LOGIN_FAILED));
        verify(accounts, never()).changePassword(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void lockedAccountIsRejectedBeforeAnyPasswordCheck() {
        MallCustomerAccountEntity account = account(2);
        when(accounts.selectByPhone("13800000000")).thenReturn(account);
        when(accounts.selectByIdForUpdate(7L)).thenReturn(account);
        // ResponseDTO 没有无参构造，直接用带参构造造一个「未通过」的检查结果
        ResponseDTO<LoginFailEntity> locked = new ResponseDTO<>(ResponseDTO.OK_CODE, null, false, "登录失败次数过多");
        when(securityLogin.checkLogin(7L, UserTypeEnum.MALL_CUSTOMER)).thenReturn(locked);
        MallLoginForm form = new MallLoginForm();
        form.setPhone("13800000000");
        form.setPassword("whatever");

        assertThatThrownBy(() -> service.login(form, "127.0.0.1"))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(MallErrorCode.LOGIN_LOCKED));
        // 锁定期内不再累加计数，也不签发会话
        verify(securityLogin, never()).recordLoginFail(any(), any(), any(), any());
        verify(accounts, never()).recordLogin(any(), any());
    }

    @Test
    void wrongPasswordRecordsFailureSoConsecutiveAttemptsCanLock() {
        MallCustomerAccountEntity account = account(2);
        account.setPasswordHash(SecurityPasswordService.getEncryptPwd("right-password"));
        when(accounts.selectByPhone("13800000000")).thenReturn(account);
        when(accounts.selectByIdForUpdate(7L)).thenReturn(account);
        LoginFailEntity fail = new LoginFailEntity();
        fail.setLoginFailCount(1);
        when(securityLogin.checkLogin(7L, UserTypeEnum.MALL_CUSTOMER)).thenReturn(ResponseDTO.ok(fail));
        MallLoginForm form = new MallLoginForm();
        form.setPhone("13800000000");
        form.setPassword("wrong-password");

        assertThatThrownBy(() -> service.login(form, "127.0.0.1"))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(MallErrorCode.LOGIN_FAILED));
        // 失败计数必须落库（login 的 noRollbackFor 就是为这条），否则连续输错永远锁不上
        verify(securityLogin).recordLoginFail(7L, UserTypeEnum.MALL_CUSTOMER, "13800000000", fail);
        verify(accounts, never()).recordLogin(any(), any());
    }

    @Test
    void successfulLoginClearsFailureCountAndSignsSessionWithCredentialVersion() {
        MallCustomerAccountEntity account = account(2);
        account.setPasswordHash(SecurityPasswordService.getEncryptPwd("right-password"));
        when(accounts.selectByPhone("13800000000")).thenReturn(account);
        when(accounts.selectByIdForUpdate(7L)).thenReturn(account);
        when(securityLogin.checkLogin(7L, UserTypeEnum.MALL_CUSTOMER)).thenReturn(ResponseDTO.ok());
        CustomerEntity customer = new CustomerEntity();
        customer.setId(9L);
        customer.setName("测试客户");
        when(customers.requireTradable(9L)).thenReturn(customer);
        MallLoginForm form = new MallLoginForm();
        form.setPhone("13800000000");
        form.setPassword("right-password");

        try (MockedStatic<StpUtil> ignored = mockStatic(StpUtil.class)) {
            service.login(form, "127.0.0.1");
            // 登录身份带凭证版本，改密后旧令牌解析不出身份
            ignored.verify(() -> StpUtil.login(MallAuthService.loginId(7L, 2), "MALL"));
        }

        verify(securityLogin).removeLoginFail(7L, UserTypeEnum.MALL_CUSTOMER);
        verify(accounts).recordLogin(7L, "127.0.0.1");
    }

    private static MallCustomerAccountEntity account(int version) {
        MallCustomerAccountEntity account = new MallCustomerAccountEntity();
        account.setId(7L);
        account.setCustomerId(9L);
        account.setLoginPhone("13800000000");
        account.setStatus("ENABLED");
        account.setVersion(version);
        account.setFirstPasswordChange(false);
        return account;
    }

    private static HttpServletRequest request() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        return request;
    }
}
