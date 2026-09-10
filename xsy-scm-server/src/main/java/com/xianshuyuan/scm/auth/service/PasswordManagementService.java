package com.xianshuyuan.scm.auth.service;

import com.xianshuyuan.scm.auth.AuthErrorCodes;
import com.xianshuyuan.scm.auth.dto.ChangePasswordRequest;
import com.xianshuyuan.scm.auth.security.SystemUserDetails;
import com.xianshuyuan.scm.auth.validation.PasswordPolicies;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.common.exception.ErrorCode;
import com.xianshuyuan.scm.system.SystemErrorCodes;
import com.xianshuyuan.scm.system.dto.ResetPasswordRequest;
import com.xianshuyuan.scm.system.entity.SystemUserEntity;
import com.xianshuyuan.scm.system.mapper.SystemUserMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PasswordManagementService {
    private static final Logger log = LoggerFactory.getLogger(PasswordManagementService.class);
    private final SystemUserMapper users;
    private final PasswordEncoder passwords;
    private final UserSessionService sessions;

    @Transactional
    public void changeOwn(ChangePasswordRequest request, Authentication authentication) {
        SystemUserDetails principal = principal(authentication);
        validatePolicy(request.newPassword());
        users.lockSecurityWrites();
        SystemUserEntity user = require(users.lockActiveUser(principal.getUser().userId()));
        requireUsable(user);
        if (!Objects.equals(user.getVersion(), request.version())) throw new BusinessException(ErrorCode.DATA_CONFLICT);
        requireCurrentSession(user, principal);
        if (!passwords.matches(request.currentPassword(), user.getPasswordHash()))
            throw new BusinessException(SystemErrorCodes.PASSWORD_CURRENT_INVALID);
        rejectReuse(request.newPassword(), user);
        update(user, request.newPassword(), false, user.getId(), user.getUsername(), "USER_PASSWORD_CHANGE");
    }

    @Transactional
    public void reset(long targetId, ResetPasswordRequest request, Authentication authentication) {
        SystemUserDetails principal = principal(authentication);
        if (principal.getUser().userId() == targetId) throw new BusinessException(SystemErrorCodes.PROTECTED_USER);
        validatePolicy(request.newPassword());
        users.lockSecurityWrites();
        long actorId = principal.getUser().userId();
        SystemUserEntity first = require(users.lockActiveUser(Math.min(actorId, targetId)));
        SystemUserEntity second = require(users.lockActiveUser(Math.max(actorId, targetId)));
        SystemUserEntity actor = first.getId() == actorId ? first : second;
        SystemUserEntity target = first.getId() == targetId ? first : second;
        requireUsable(actor);
        requireCurrentSession(actor, principal);
        if (Boolean.TRUE.equals(actor.getMustChangePassword()))
            throw new BusinessException(AuthErrorCodes.PASSWORD_CHANGE_REQUIRED);
        if (!Boolean.TRUE.equals(actor.getAdministrator()))
            throw new BusinessException(AuthErrorCodes.PERMISSION_DENIED);
        requireUsable(target);
        if (!Objects.equals(target.getVersion(), request.version()))
            throw new BusinessException(ErrorCode.DATA_CONFLICT);
        rejectReuse(request.newPassword(), target);
        update(target, request.newPassword(), true, actor.getId(), actor.getUsername(), "USER_PASSWORD_RESET");
    }

    private void update(SystemUserEntity target, String rawPassword, boolean mustChange, long actorId,
                        String actorName, String operation) {
        String hash = passwords.encode(rawPassword);
        if (users.updatePassword(target.getId(), target.getVersion(), hash, actorName, mustChange) != 1)
            throw new BusinessException(ErrorCode.DATA_CONFLICT);
        String after = "{\"mustChangePassword\":" + mustChange + "}";
        users.insertAudit(target.getId(), actorId, actorName, operation, null, after);
        invalidateAfterCommit(target.getUsername());
    }

    private void validatePolicy(String value) {
        if (!PasswordPolicies.valid(value)) throw new BusinessException(SystemErrorCodes.PASSWORD_POLICY);
    }

    private void rejectReuse(String candidate, SystemUserEntity user) {
        if (user.getPasswordHash() != null && passwords.matches(candidate, user.getPasswordHash()))
            throw new BusinessException(SystemErrorCodes.PASSWORD_REUSED);
    }

    private SystemUserDetails principal(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SystemUserDetails details))
            throw new BusinessException(AuthErrorCodes.LOGIN_REQUIRED);
        return details;
    }

    private void requireCurrentSession(SystemUserEntity user, SystemUserDetails principal) {
        if (!Objects.equals(user.getAuthVersion(), principal.getUser().authVersion()))
            throw new BusinessException(AuthErrorCodes.SESSION_INVALID);
    }

    private SystemUserEntity require(SystemUserEntity user) {
        if (user == null) throw new BusinessException(SystemErrorCodes.USER_NOT_FOUND);
        return user;
    }

    private void requireUsable(SystemUserEntity user) {
        if (!"ENABLED".equals(user.getStatus()) || Boolean.TRUE.equals(user.getDeleted())
                || user.getLockedUntil() != null && user.getLockedUntil().isAfter(OffsetDateTime.now()))
            throw new BusinessException(SystemErrorCodes.PASSWORD_TARGET_UNUSABLE);
    }

    private void invalidateAfterCommit(String username) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    sessions.invalidateAll(username);
                } catch (RuntimeException failure) {
                    log.warn("Session invalidation failed after password update; version checks remain active");
                }
            }
        });
    }
}
