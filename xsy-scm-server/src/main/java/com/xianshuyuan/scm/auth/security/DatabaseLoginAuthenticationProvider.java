package com.xianshuyuan.scm.auth.security;

import com.xianshuyuan.scm.auth.service.LoginLogService;
import com.xianshuyuan.scm.auth.service.UserSessionService;
import com.xianshuyuan.scm.system.entity.SystemUserEntity;
import com.xianshuyuan.scm.system.mapper.SystemUserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Component
@lombok.extern.slf4j.Slf4j
public class DatabaseLoginAuthenticationProvider implements AuthenticationProvider {
    private final SystemUserMapper users;
    private final SystemUserDetailsService userDetailsService;
    private final UserSessionService sessions;
    private final PasswordEncoder passwords;
    private final TransactionTemplate transactions;
    private final LoginLogService loginLogs;
    private final int failureThreshold;
    private final Duration lockDuration;
    private final String dummyPasswordHash;

    public DatabaseLoginAuthenticationProvider(
            SystemUserMapper users,
            SystemUserDetailsService userDetailsService,
            UserSessionService sessions,
            PasswordEncoder passwords,
            TransactionTemplate transactions,
            @Value("${xsy.auth.login.failure-threshold:5}") int failureThreshold,
            @Value("${xsy.auth.login.lock-duration:15m}") Duration lockDuration,
            LoginLogService loginLogs
    ) {
        if (failureThreshold < 1 || lockDuration.isZero() || lockDuration.isNegative()) {
            throw new IllegalArgumentException("Login lockout configuration must be positive");
        }
        this.users = users;
        this.userDetailsService = userDetailsService;
        this.sessions = sessions;
        this.passwords = passwords;
        this.transactions = transactions;
        this.loginLogs = loginLogs;
        this.failureThreshold = failureThreshold;
        this.lockDuration = lockDuration;
        this.dummyPasswordHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName() == null ? "" : authentication.getName().trim();
        String presentedPassword = authentication.getCredentials() == null ? "" : authentication.getCredentials().toString();
        LoginRequestDetails request = authentication.getDetails() instanceof LoginRequestDetails details
                ? details : new LoginRequestDetails(null, null);
        Outcome outcome = transactions.execute(status -> authenticateLocked(username, presentedPassword, request));
        if (outcome == null || outcome.authentication() == null) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return outcome.authentication();
    }

    private Outcome authenticateLocked(String username, String presentedPassword, LoginRequestDetails request) {
        users.lockSecurityWrites();
        SystemUserEntity user = users.lockActiveUserByUsername(username);
        if (user == null) {
            passwords.matches(presentedPassword, dummyPasswordHash);
            recordLog(null, username, "FAILURE", "INVALID_CREDENTIALS", request);
            return Outcome.failure(null, false);
        }
        if (!"ENABLED".equals(user.getStatus()) || user.getPasswordHash() == null) {
            passwords.matches(presentedPassword, dummyPasswordHash);
            recordLog(user.getId(), user.getUsername(), "FAILURE", "ACCOUNT_UNUSABLE", request);
            return Outcome.failure(user.getUsername(), false);
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            passwords.matches(presentedPassword, dummyPasswordHash);
            recordLog(user.getId(), user.getUsername(), "LOCKED", "ACCOUNT_LOCKED", request);
            return Outcome.failure(user.getUsername(), false);
        }
        if (!passwords.matches(presentedPassword, user.getPasswordHash())) {
            int previous = user.getLockedUntil() != null ? 0 : user.getFailedLoginCount();
            int count = previous + 1;
            OffsetDateTime lockedUntil = count >= failureThreshold ? now.plus(lockDuration) : null;
            boolean newlyLocked = lockedUntil != null;
            users.recordLoginFailure(user.getId(), count, lockedUntil, newlyLocked);
            recordLog(user.getId(), user.getUsername(), newlyLocked ? "LOCKED" : "FAILURE",
                    newlyLocked ? "LOGIN_FAILURE_THRESHOLD" : "INVALID_CREDENTIALS", request);
            if (newlyLocked) {
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                try {
                                    sessions.invalidateAll(user.getUsername());
                                } catch (RuntimeException ignored) {
                                    // auth_version is the authoritative fallback for cleanup failure.
                                    log.warn("Login lockout session cleanup failed for user ID {}", user.getId());
                                }
                            }
                        });
            }
            return Outcome.failure(user.getUsername(), newlyLocked);
        }

        users.recordLoginSuccess(user.getId(), now);
        recordLog(user.getId(), user.getUsername(), "SUCCESS", null, request);
        UserDetails details = userDetailsService.loadUserByUsername(user.getUsername());
        return Outcome.success(UsernamePasswordAuthenticationToken.authenticated(
                details, null, details.getAuthorities()));
    }

    private void recordLog(Long userId, String username, String result, String reason, LoginRequestDetails request) {
        try {
            loginLogs.append(userId, username, result, reason, request.ip(), request.userAgent());
        } catch (RuntimeException ignored) {
            // Authentication outcome must not depend on audit persistence.
            log.warn("Login audit persistence failed for result {} and user ID {}", result, userId);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private record Outcome(Authentication authentication, String username, boolean invalidateSessions) {
        static Outcome success(Authentication authentication) { return new Outcome(authentication, null, false); }
        static Outcome failure(String username, boolean invalidate) { return new Outcome(null, username, invalidate); }
    }
}
