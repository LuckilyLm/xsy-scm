package com.xsy.scm.mall.account.service;

import cn.dev33.satoken.stp.StpUtil;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.mall.account.constant.MallAccountStatusEnum;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.account.constant.MallLoginMethodEnum;
import com.xsy.scm.mall.account.dao.MallCustomerAccountDao;
import com.xsy.scm.mall.account.domain.entity.MallCustomerAccountEntity;
import com.xsy.scm.mall.account.domain.form.MallLoginForm;
import com.xsy.scm.mall.account.domain.form.MallPasswordChangeForm;
import com.xsy.scm.mall.account.domain.form.MallWechatPhoneLoginForm;
import com.xsy.scm.mall.account.domain.vo.MallLoginVO;
import com.xsy.scm.mall.account.domain.vo.MallProfileVO;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.account.exception.MallLoginRejectedException;
import com.xsy.scm.order.constant.ScmOrderSourceEnum;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Objects;

import lombok.RequiredArgsConstructor;
import com.xsy.scm.base.common.enumeration.UserTypeEnum;
import com.xsy.scm.base.module.support.securityprotect.service.SecurityPasswordService;
import com.xsy.scm.base.module.support.securityprotect.service.SecurityLoginService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MallAuthService {
    private final MallCustomerAccountDao accountDao;
    private final CustomerService customerService;
    private final SecurityLoginService securityLoginService;
    private final MallWechatService wechatService;

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

    /**
     * @param form
     *            一次性微信手机号授权code，不得为空；手机号仅从微信获取
     * @param ip
     *            当前请求来源地址，可为空，由现有登录记录保存
     * @return 已开通账号的独立微信会话，免临时密码修改
     * @throws ScmBusinessException
     *             授权无效、账号未开通/不可用或客户不可交易时拒绝
     */
    @Transactional(rollbackFor = Exception.class)
    public MallLoginVO wechatPhoneLogin(MallWechatPhoneLoginForm form, String ip) {
        String phone = wechatService.phone(form.getCode());
        MallCustomerAccountEntity account = accountDao.selectByPhone(phone);
        if (account != null) {
            account = accountDao.selectByIdForUpdate(account.getId());
        }
        if (account == null || !phone.equals(account.getLoginPhone())) {
            throw new ScmBusinessException(MallErrorCode.WECHAT_ACCOUNT_NOT_OPENED);
        }
        if (!MallAccountStatusEnum.ENABLED.name().equals(account.getStatus())) {
            throw new ScmBusinessException(MallErrorCode.ACCOUNT_DISABLED);
        }
        if (!Boolean.TRUE
            .equals(securityLoginService.checkLogin(account.getId(), UserTypeEnum.MALL_CUSTOMER).getOk())) {
            throw new ScmBusinessException(MallErrorCode.LOGIN_LOCKED);
        }
        CustomerEntity customer = customerService.requireTradable(account.getCustomerId());
        securityLoginService.removeLoginFail(account.getId(), UserTypeEnum.MALL_CUSTOMER);
        accountDao.recordLogin(account.getId(), ip);
        // 认证来源是登录身份的一部分，Sa-Token不会将已有密码令牌升级为微信豁免会话。
        StpUtil.login(loginId(account.getId(), account.getVersion()) + ":" + MallLoginMethodEnum.WECHAT_PHONE.name(),
            ScmOrderSourceEnum.MALL.name());
        return new MallLoginVO(StpUtil.getTokenValue(), profile(account, customer, MallLoginMethodEnum.WECHAT_PHONE));
    }

    public void logout() {
        StpUtil.logout();
    }

    @Transactional(readOnly = true)
    public MallProfileVO profile(RequestMallCustomer requestCustomer) {
        MallCustomerAccountEntity account = requireEnabled(requestCustomer.getAccountId());
        CustomerEntity customer = customerService.requireTradable(account.getCustomerId());
        return profile(account, customer, requestCustomer.getLoginMethod());
    }

    @Transactional(rollbackFor = Exception.class, noRollbackFor = MallLoginRejectedException.class)
    public MallProfileVO changePassword(RequestMallCustomer requestCustomer, MallPasswordChangeForm form) {
        MallCustomerAccountEntity account = accountDao.selectByIdForUpdate(requestCustomer.getAccountId());
        // 过期会话不能消耗当前账号的密码尝试次数，凭证版本在账号行锁内重新核对。
        if (account == null || !Objects.equals(requestCustomer.getSessionVersion(), account.getVersion())) {
            throw new ScmBusinessException(MallErrorCode.LOGIN_FAILED);
        }
        if (!MallAccountStatusEnum.ENABLED.name().equals(account.getStatus())) {
            throw new ScmBusinessException(MallErrorCode.ACCOUNT_DISABLED);
        }
        var loginCheck = securityLoginService.checkLogin(account.getId(), UserTypeEnum.MALL_CUSTOMER);
        if (!Boolean.TRUE.equals(loginCheck.getOk())) {
            throw new MallLoginRejectedException(MallErrorCode.LOGIN_LOCKED);
        }
        if (!SecurityPasswordService.matchesPwd(form.getOldPassword(), account.getPasswordHash())) {
            securityLoginService.recordLoginFail(account.getId(), UserTypeEnum.MALL_CUSTOMER, account.getLoginPhone(),
                loginCheck.getData());
            // 与密码登录共用计数；拒绝异常不回滚，保证连续输错后限制仍然生效。
            throw new MallLoginRejectedException(MallErrorCode.LOGIN_FAILED);
        }
        // 旧密码正确后才提示新旧相同，避免用该提示绕过密码核验及失败计数。
        if (Objects.equals(form.getNewPassword(), form.getOldPassword())) {
            throw new ScmBusinessException(MallErrorCode.PASSWORD_SAME_AS_OLD);
        }
        accountDao.changePassword(account.getId(), SecurityPasswordService.getEncryptPwd(form.getNewPassword()));
        securityLoginService.removeLoginFail(account.getId(), UserTypeEnum.MALL_CUSTOMER);
        account.setFirstPasswordChange(false);
        return profile(account, customerService.requireTradable(account.getCustomerId()),
            requestCustomer.getLoginMethod());
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
        result.setLoginMethod(identity.loginMethod());
        result.setFirstPasswordChange(
            identity.loginMethod() == MallLoginMethodEnum.WECHAT_PHONE ? false : account.getFirstPasswordChange());
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
        return profile(account, customer, MallLoginMethodEnum.PASSWORD);
    }

    private static MallProfileVO profile(MallCustomerAccountEntity account, CustomerEntity customer,
        MallLoginMethodEnum loginMethod) {
        return new MallProfileVO(account.getId(), customer.getId(), customer.getName(), account.getLoginPhone(),
            loginMethod == MallLoginMethodEnum.WECHAT_PHONE ? false : account.getFirstPasswordChange());
    }

    public static String loginId(Long accountId, Integer version) {
        // 版本进入登录身份，避免 Sa-Token 复用旧令牌时把已失效的会话重新激活。
        // 业务审计与防重复处理仍由 RequestMallCustomer 的账号 ID 生成，不随会话版本变化。
        return UserTypeEnum.MALL_CUSTOMER.getValue() + ":" + accountId + ":" + version;
    }

    private static SessionIdentity sessionIdentity(String loginId) {
        if (loginId == null || !loginId.startsWith(UserTypeEnum.MALL_CUSTOMER.getValue() + ":")) {
            return null;
        }
        try {
            String[] parts = loginId.split(":", -1);
            if (parts.length != 3 && (parts.length != 4 || !MallLoginMethodEnum.WECHAT_PHONE.name().equals(parts[3]))) {
                return null;
            }
            Long accountId = Long.valueOf(parts[1]);
            Integer version = Integer.valueOf(parts[2]);
            if (accountId <= 0 || version < 0) {
                return null;
            }
            return new SessionIdentity(accountId, version,
                parts.length == 4 ? MallLoginMethodEnum.WECHAT_PHONE : MallLoginMethodEnum.PASSWORD);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private record SessionIdentity(Long accountId, Integer version, MallLoginMethodEnum loginMethod) {
    }
}
