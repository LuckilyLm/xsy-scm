package com.xianshuyuan.scm.mall.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.customer.entity.CustomerEntity;
import com.xianshuyuan.scm.customer.service.CustomerService;
import com.xianshuyuan.scm.mall.entity.MallCustomerAccountEntity;
import com.xianshuyuan.scm.mall.entity.MallCustomerSessionEntity;
import com.xianshuyuan.scm.mall.mapper.MallCustomerAccountMapper;
import com.xianshuyuan.scm.mall.mapper.MallCustomerSessionMapper;
import com.xianshuyuan.scm.mall.security.MallCustomerPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 商城客户会话。令牌只保存摘要，服务端不存储明文；客户身份与后台员工身份完全隔离。
 */
@Service
public class MallSessionService {

    private static final int TOKEN_BYTES = 32;
    private static final String ENABLED = "ENABLED";

    private final MallCustomerAccountMapper accounts;
    private final MallCustomerSessionMapper sessions;
    private final CustomerService customers;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();
    private final long sessionTtlHours;

    public MallSessionService(MallCustomerAccountMapper accounts, MallCustomerSessionMapper sessions,
                              CustomerService customers, PasswordEncoder passwordEncoder,
                              @Value("${xsy.mall.session.ttl-hours:168}") long sessionTtlHours) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.customers = customers;
        this.passwordEncoder = passwordEncoder;
        this.sessionTtlHours = sessionTtlHours;
    }

    @Transactional
    public LoginResult login(String username, String password, String userAgent) {
        MallCustomerAccountEntity account = accounts.selectActiveByUsername(username == null ? "" : username.trim());
        if (account == null || !passwordEncoder.matches(String.valueOf(password), account.getPasswordHash())) {
            throw new BusinessException(MallErrorCodes.LOGIN_FAILED);
        }
        if (!ENABLED.equals(account.getStatus())) {
            throw new BusinessException(MallErrorCodes.ACCOUNT_DISABLED);
        }
        MallCustomerPrincipal principal = principalOf(account);
        String token = issueToken(account, userAgent);
        accounts.updateLastLoginAt(account.getId());
        return new LoginResult(principal, token, OffsetDateTime.now().plusHours(sessionTtlHours));
    }

    public Optional<MallCustomerPrincipal> resolve(String token) {
        MallCustomerSessionEntity session = sessions.selectActiveByTokenHash(hash(token));
        if (session == null || session.getExpiresAt() == null
                || session.getExpiresAt().isBefore(OffsetDateTime.now())) {
            return Optional.empty();
        }
        MallCustomerAccountEntity account = accounts.selectById(session.getAccountId());
        if (account == null || Boolean.TRUE.equals(account.getDeleted()) || !ENABLED.equals(account.getStatus())) {
            return Optional.empty();
        }
        try {
            CustomerEntity customer = customers.require(account.getCustomerId());
            if (!ENABLED.equals(customer.getStatus())) {
                return Optional.empty();
            }
            sessions.touch(session.getId());
            return Optional.of(new MallCustomerPrincipal(account.getId(), account.getCustomerId(),
                    customer.getCustomerCode(), customer.getName(), account.getUsername(),
                    account.getWechatOpenid() != null));
        } catch (BusinessException exception) {
            return Optional.empty();
        }
    }

    @Transactional
    public void revoke(String token) {
        sessions.revokeByTokenHash(hash(token));
    }

    public MallCustomerPrincipal principalOf(MallCustomerAccountEntity account) {
        CustomerEntity customer;
        try {
            customer = customers.require(account.getCustomerId());
        } catch (BusinessException exception) {
            throw new BusinessException(MallErrorCodes.CUSTOMER_DISABLED);
        }
        if (!ENABLED.equals(customer.getStatus())) {
            throw new BusinessException(MallErrorCodes.CUSTOMER_DISABLED);
        }
        return new MallCustomerPrincipal(account.getId(), account.getCustomerId(), customer.getCustomerCode(),
                customer.getName(), account.getUsername(), account.getWechatOpenid() != null);
    }

    private String issueToken(MallCustomerAccountEntity account, String userAgent) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        MallCustomerSessionEntity session = new MallCustomerSessionEntity();
        session.setAccountId(account.getId());
        session.setCustomerId(account.getCustomerId());
        session.setTokenHash(hash(token));
        session.setUserAgent(trim(userAgent));
        session.setExpiresAt(OffsetDateTime.now().plusHours(sessionTtlHours));
        session.setCreatedBy("MALL");
        sessions.insert(session);
        return token;
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 500) {
            return trimmed;
        }
        return trimmed.substring(0, 500);
    }

    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("缺少 SHA-256 算法支持", exception);
        }
    }

    public record LoginResult(MallCustomerPrincipal principal, String token, OffsetDateTime expiresAt) {
    }
}
