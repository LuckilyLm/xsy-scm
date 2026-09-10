package com.xianshuyuan.scm.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianshuyuan.scm.auth.security.AuthorityRules;
import com.xianshuyuan.scm.auth.service.UserSessionService;
import com.xianshuyuan.scm.common.api.PageData;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.common.exception.ErrorCode;
import com.xianshuyuan.scm.system.SystemErrorCodes;
import com.xianshuyuan.scm.system.converter.UserConverter;
import com.xianshuyuan.scm.system.dto.*;
import com.xianshuyuan.scm.system.entity.SystemUserEntity;
import com.xianshuyuan.scm.system.mapper.SystemUserMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final Set<String> RESERVED = Set.of("admin", "administrator", "system", "root");
    private final SystemUserMapper users;
    private final UserConverter converter;
    private final PasswordEncoder passwords;
    private final UserSessionService sessions;
    private final ObjectMapper json;

    public PageData<UserResponse> list(UserQuery query) {
        var wrapper = new LambdaQueryWrapper<SystemUserEntity>();
        String keyword = optional(query.getKeyword());
        if (keyword != null) wrapper.and(w -> w.like(SystemUserEntity::getUsername, keyword)
                .or().like(SystemUserEntity::getDisplayName, keyword));
        wrapper.eq(query.getStatus() != null, SystemUserEntity::getStatus, query.getStatus());
        wrapper.eq(query.getDepartmentId() != null, SystemUserEntity::getDepartmentId, query.getDepartmentId());
        wrapper.orderByDesc(SystemUserEntity::getId);
        var page = users.selectPage(new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return new PageData<>(page.getRecords().stream().map(converter::toResponse).toList(), page.getCurrent(), page.getSize(), page.getTotal());
    }

    public UserResponse detail(long id) {
        return converter.toResponse(require(users.selectById(id)));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request, Authentication actor) {
        users.lockSecurityWrites();
        String username = request.getUsername().strip().toLowerCase(Locale.ROOT);
        if (!username.matches("[a-z0-9][a-z0-9._-]{2,63}") || RESERVED.contains(username)
                || request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        department(request.getDepartmentId());
        if (users.selectActiveByUsername(username) != null) throw new BusinessException(ErrorCode.DATA_CONFLICT);
        var user = new SystemUserEntity();
        user.setUsername(username);
        user.setDisplayName(request.getDisplayName().strip());
        user.setDepartmentId(request.getDepartmentId());
        user.setEmail(email(request.getEmail()));
        user.setPhone(optional(request.getPhone()));
        user.setPasswordHash(passwords.encode(request.getPassword()));
        user.setAdministrator(false);
        user.setMustChangePassword(true);
        user.setStatus("ENABLED");
        user.setAuthVersion(0L);
        user.setFailedLoginCount(0);
        user.setVersion(0);
        user.setDeleted(false);
        user.setCreatedBy(actor.getName());
        user.setUpdatedBy(actor.getName());
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(user.getCreatedAt());
        users.insert(user);
        UserResponse result = converter.toResponse(user);
        audit(user.getId(), actor, "USER_CREATE", null, result);
        return result;
    }

    @Transactional
    public UserResponse update(long id, UpdateUserRequest request, Authentication actor) {
        users.lockSecurityWrites();
        // Acquire department before user; future department commands use the same lock order.
        department(request.departmentId());
        var user = locked(id, request.version());
        protect(user, actor, false);
        var before = converter.toResponse(user);
        user.setDisplayName(request.displayName().strip());
        user.setDepartmentId(request.departmentId());
        user.setEmail(email(request.email()));
        user.setPhone(optional(request.phone()));
        user.setUpdatedBy(actor.getName());
        if (users.updateProfile(user) != 1) throw new BusinessException(ErrorCode.DATA_CONFLICT);
        var result = detail(id);
        audit(id, actor, "USER_UPDATE", before, result);
        invalidateAfterCommit(user.getUsername());
        return result;
    }

    @Transactional
    public UserResponse changeStatus(long id, UserStatusRequest request, Authentication actor) {
        users.lockSecurityWrites();
        var current = require(users.selectById(id));
        if ("ENABLED".equals(request.status())) department(current.getDepartmentId());
        var user = locked(id, request.version());
        protect(user, actor, "DISABLED".equals(request.status()));
        var before = converter.toResponse(user);
        if (users.updateState(id, request.version(), request.status(), false, actor.getName()) != 1)
            throw new BusinessException(ErrorCode.DATA_CONFLICT);
        var result = detail(id);
        audit(id, actor, "USER_STATUS", before, result);
        invalidateAfterCommit(user.getUsername());
        return result;
    }

    @Transactional
    public void delete(long id, int version, Authentication actor) {
        users.lockSecurityWrites();
        var user = locked(id, version);
        protect(user, actor, true);
        if (users.updateState(id, version, user.getStatus(), true, actor.getName()) != 1)
            throw new BusinessException(ErrorCode.DATA_CONFLICT);
        audit(id, actor, "USER_DELETE", converter.toResponse(user), null);
        invalidateAfterCommit(user.getUsername());
    }

    private SystemUserEntity locked(long id, int version) {
        var user = require(users.lockActiveUser(id));
        if (!Objects.equals(user.getVersion(), version)) throw new BusinessException(ErrorCode.DATA_CONFLICT);
        return user;
    }

    private SystemUserEntity require(SystemUserEntity user) {
        if (user == null) throw new BusinessException(SystemErrorCodes.USER_NOT_FOUND);
        return user;
    }

    private void department(Long id) {
        if (id != null && users.lockEnabledDepartment(id) == null)
            throw new BusinessException(SystemErrorCodes.INVALID_DEPARTMENT);
    }

    private void protect(SystemUserEntity user, Authentication actor, boolean destructive) {
        boolean reserved = RESERVED.contains(user.getUsername().toLowerCase(Locale.ROOT));
        boolean admin = Boolean.TRUE.equals(user.getAdministrator());
        if ((admin || reserved) && !AuthorityRules.isAdministrator(actor)
                || destructive && (reserved || user.getUsername().equalsIgnoreCase(actor.getName())
                || admin && users.countUsableAdministratorsExcept(user.getId()) == 0))
            throw new BusinessException(SystemErrorCodes.PROTECTED_USER);
    }

    private void invalidateAfterCommit(String username) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    sessions.invalidateAll(username);
                } catch (DataAccessException failure) {
                    // The committed authVersion is authoritative; AccountVersionFilter rejects stale sessions.
                    log.warn("Session invalidation failed after user security update; version checks remain active");
                }
            }
        });
    }

    private void audit(long id, Authentication actor, String operation, UserResponse before, UserResponse after) {
        // Login principals carry immutable identity; non-user/system actors have no user ID.
        Long actorId = actor.getPrincipal() instanceof com.xianshuyuan.scm.auth.security.SystemUserDetails details
                ? details.getUser().userId() : null;
        try {
            users.insertAudit(id, actorId, actor.getName(), operation, json.writeValueAsString(before), json.writeValueAsString(after));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot serialize user audit", failure);
        }
    }

    private String optional(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private String email(String text) {
        String value = optional(text);
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
