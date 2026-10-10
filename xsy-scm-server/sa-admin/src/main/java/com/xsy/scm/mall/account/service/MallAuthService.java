package com.xsy.scm.mall.account.service;

import cn.dev33.satoken.stp.StpUtil;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.mall.account.constant.MallAccountStatusEnum;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.account.dao.MallCustomerAccountDao;
import com.xsy.scm.mall.account.domain.entity.MallCustomerAccountEntity;
import com.xsy.scm.mall.account.domain.form.MallLoginForm;
import com.xsy.scm.mall.account.domain.form.MallPasswordChangeForm;
import com.xsy.scm.mall.account.domain.vo.MallLoginVO;
import com.xsy.scm.mall.account.domain.vo.MallProfileVO;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.module.support.securityprotect.service.SecurityPasswordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MallAuthService {
    private final MallCustomerAccountDao accountDao;
    private final CustomerService customerService;

    @Transactional(rollbackFor = Exception.class)
    public MallLoginVO login(MallLoginForm form, String ip) {
        MallCustomerAccountEntity account = accountDao.selectByPhone(MallAccountService.normalizePhone(form.getPhone()));
        if (account == null || !SecurityPasswordService.matchesPwd(form.getPassword(), account.getPasswordHash())) {
            throw new ScmBusinessException(MallErrorCode.LOGIN_FAILED);
        }
        if (!MallAccountStatusEnum.ENABLED.name().equals(account.getStatus())) {
            throw new ScmBusinessException(MallErrorCode.ACCOUNT_DISABLED);
        }
        CustomerEntity customer = customerService.requireTradable(account.getCustomerId());
        accountDao.recordLogin(account.getId(), ip);
        StpUtil.login(loginId(account.getId()), "MALL");
        return new MallLoginVO(StpUtil.getTokenValue(), profile(account, customer));
    }

    public void logout() {
        StpUtil.logout();
    }

    @Transactional(readOnly = true)
    public MallProfileVO profile(RequestMallCustomer requestCustomer) {
        MallCustomerAccountEntity account = requireEnabled(requestCustomer.getAccountId());
        CustomerEntity customer = customerService.requireTradable(account.getCustomerId());
        return profile(account, customer);
    }

    @Transactional(rollbackFor = Exception.class)
    public MallProfileVO changePassword(RequestMallCustomer requestCustomer, MallPasswordChangeForm form) {
        MallCustomerAccountEntity account = accountDao.selectByIdForUpdate(requestCustomer.getAccountId());
        if (account == null || !SecurityPasswordService.matchesPwd(form.getOldPassword(), account.getPasswordHash())) {
            throw new ScmBusinessException(MallErrorCode.LOGIN_FAILED);
        }
        accountDao.changePassword(account.getId(), SecurityPasswordService.getEncryptPwd(form.getNewPassword()));
        account.setFirstPasswordChange(false);
        return profile(account, customerService.requireTradable(account.getCustomerId()));
    }

    @Transactional(readOnly = true)
    public RequestMallCustomer requestCustomer(String loginId, HttpServletRequest request) {
        Long accountId = accountId(loginId);
        if (accountId == null) {
            return null;
        }
        MallCustomerAccountEntity account = requireEnabled(accountId);
        CustomerEntity customer = customerService.requireTradable(account.getCustomerId());
        RequestMallCustomer result = new RequestMallCustomer();
        result.setAccountId(account.getId());
        result.setCustomerId(customer.getId());
        result.setCustomerName(customer.getName());
        result.setLoginPhone(account.getLoginPhone());
        result.setFirstPasswordChange(account.getFirstPasswordChange());
        result.setIp(request.getRemoteAddr());
        result.setUserAgent(request.getHeader("User-Agent"));
        return result;
    }

    private MallCustomerAccountEntity requireEnabled(Long accountId) {
        MallCustomerAccountEntity account = accountDao.selectById(accountId);
        if (account == null || !MallAccountStatusEnum.ENABLED.name().equals(account.getStatus())) {
            throw new ScmBusinessException(MallErrorCode.ACCOUNT_DISABLED);
        }
        return account;
    }

    private static MallProfileVO profile(MallCustomerAccountEntity account, CustomerEntity customer) {
        return new MallProfileVO(account.getId(), customer.getId(), customer.getName(), account.getLoginPhone(),
                account.getFirstPasswordChange());
    }

    public static String loginId(Long accountId) {
        return UserTypeEnum.MALL_CUSTOMER.getValue() + ":" + accountId;
    }

    private static Long accountId(String loginId) {
        if (loginId == null || !loginId.startsWith(UserTypeEnum.MALL_CUSTOMER.getValue() + ":")) {
            return null;
        }
        try {
            return Long.valueOf(loginId.substring(loginId.indexOf(':') + 1));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
