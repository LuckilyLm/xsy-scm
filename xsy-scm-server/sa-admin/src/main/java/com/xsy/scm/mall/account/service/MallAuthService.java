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
import com.xsy.scm.mall.account.exception.MallLoginRejectedException;
import com.xsy.scm.order.constant.ScmOrderSourceEnum;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.module.support.securityprotect.service.SecurityPasswordService;
import net.lab1024.sa.base.module.support.securityprotect.service.SecurityLoginService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MallAuthService {
    private final MallCustomerAccountDao accountDao;
    private final CustomerService customerService;
    private final SecurityLoginService securityLoginService;

    @Transactional(rollbackFor = Exception.class, noRollbackFor = MallLoginRejectedException.class)
    public MallLoginVO login(MallLoginForm form, String ip) {
        MallCustomerAccountEntity account = accountDao
                .selectByPhone(MallAccountService.normalizePhone(form.getPhone()));
        if (account != null) {
            // 同账号登录串行计数，并与改密/停用共用行锁，不能拿旧凭证签发新会话。
            account = accountDao.selectByIdForUpdate(account.getId());
        }
        if (account == null) {
            throw new MallLoginRejectedException(MallErrorCode.LOGIN_FAILED);
        }
        if (!MallAccountStatusEnum.ENABLED.name().equals(account.getStatus())) {
            throw new ScmBusinessException(MallErrorCode.ACCOUNT_DISABLED);
        }
        var loginCheck = securityLoginService.checkLogin(account.getId(), UserTypeEnum.MALL_CUSTOMER);
        if (!Boolean.TRUE.equals(loginCheck.getOk())) {
            throw new MallLoginRejectedException(MallErrorCode.LOGIN_LOCKED);
        }
        if (!SecurityPasswordService.matchesPwd(form.getPassword(), account.getPasswordHash())) {
            securityLoginService.recordLoginFail(account.getId(), UserTypeEnum.MALL_CUSTOMER, account.getLoginPhone(),
                    loginCheck.getData());
            throw new MallLoginRejectedException(MallErrorCode.LOGIN_FAILED);
        }
        CustomerEntity customer = customerService.requireTradable(account.getCustomerId());
        securityLoginService.removeLoginFail(account.getId(), UserTypeEnum.MALL_CUSTOMER);
        accountDao.recordLogin(account.getId(), ip);
        StpUtil.login(loginId(account.getId(), account.getVersion()), ScmOrderSourceEnum.MALL.name());
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
        if (!Objects.equals(requestCustomer.getSessionVersion(), account.getVersion())) {
            throw new ScmBusinessException(MallErrorCode.LOGIN_FAILED);
        }
        accountDao.changePassword(account.getId(), SecurityPasswordService.getEncryptPwd(form.getNewPassword()));
        securityLoginService.removeLoginFail(account.getId(), UserTypeEnum.MALL_CUSTOMER);
        account.setFirstPasswordChange(false);
        return profile(account, customerService.requireTradable(account.getCustomerId()));
    }

    @Transactional(readOnly = true)
    public RequestMallCustomer requestCustomer(String loginId, HttpServletRequest request) {
        SessionIdentity identity = sessionIdentity(loginId);
        if (identity == null) {
            return null;
        }
        MallCustomerAccountEntity account = requireEnabled(identity.accountId());
        if (!Objects.equals(account.getVersion(), identity.version())) {
            return null;
        }
        CustomerEntity customer = customerService.requireTradable(account.getCustomerId());
        RequestMallCustomer result = new RequestMallCustomer();
        result.setAccountId(account.getId());
        result.setSessionVersion(account.getVersion());
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

    public static String loginId(Long accountId, Integer version) {
        // 版本进入登录身份，避免 Sa-Token 复用旧令牌时把已失效的会话重新激活。
        // 业务审计与幂等仍由 RequestMallCustomer 的账号 ID 生成，不随会话版本变化。
        return UserTypeEnum.MALL_CUSTOMER.getValue() + ":" + accountId + ":" + version;
    }

    private static SessionIdentity sessionIdentity(String loginId) {
        if (loginId == null || !loginId.startsWith(UserTypeEnum.MALL_CUSTOMER.getValue() + ":")) {
            return null;
        }
        try {
            String[] parts = loginId.split(":", -1);
            if (parts.length != 3) {
                return null;
            }
            return new SessionIdentity(Long.valueOf(parts[1]), Integer.valueOf(parts[2]));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private record SessionIdentity(Long accountId, Integer version) {
    }
}
