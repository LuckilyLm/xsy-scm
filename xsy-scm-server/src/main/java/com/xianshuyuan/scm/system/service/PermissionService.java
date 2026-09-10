package com.xianshuyuan.scm.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianshuyuan.scm.auth.security.AuthorityRules;
import com.xianshuyuan.scm.auth.security.SystemUserDetails;
import com.xianshuyuan.scm.auth.service.UserSessionService;
import com.xianshuyuan.scm.common.api.PageData;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.common.exception.ErrorCode;
import com.xianshuyuan.scm.system.SystemErrorCodes;
import com.xianshuyuan.scm.system.converter.PermissionConverter;
import com.xianshuyuan.scm.system.dto.*;
import com.xianshuyuan.scm.system.entity.PermissionEntity;
import com.xianshuyuan.scm.system.mapper.PermissionMapper;
import com.xianshuyuan.scm.system.mapper.SystemUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionService {
    private final PermissionMapper permissions;
    private final SystemUserMapper users;
    private final PermissionConverter converter;
    private final UserSessionService sessions;
    private final ObjectMapper json;

    public PageData<PermissionResponse> list(PermissionQuery query) {
        var wrapper = new LambdaQueryWrapper<PermissionEntity>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String keyword = query.getKeyword().strip();
            wrapper.and(w -> w.like(PermissionEntity::getCode, keyword).or().like(PermissionEntity::getName, keyword));
        }
        wrapper.eq(query.getModule() != null, PermissionEntity::getModule, query.getModule());
        wrapper.eq(query.getType() != null, PermissionEntity::getResourceType, query.getType());
        wrapper.eq(query.getStatus() != null, PermissionEntity::getStatus, query.getStatus());
        wrapper.orderByAsc(PermissionEntity::getId);
        var page = permissions.selectPage(new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return new PageData<>(page.getRecords().stream().map(converter::toResponse).toList(), page.getCurrent(), page.getSize(), page.getTotal());
    }

    public PermissionResponse detail(long id) {
        return converter.toResponse(require(permissions.selectById(id)));
    }

    @Transactional
    public PermissionResponse create(CreatePermissionRequest request, Authentication actor) {
        users.lockSecurityWrites();
        var current = currentActor(actor, "create");
        String code = normalize(request.permissionCode());
        validateCode(code);
        if (AuthorityRules.isReservedPermission(code, false)) forbidden();
        if (permissions.countCode(code) > 0) throw new BusinessException(ErrorCode.DATA_CONFLICT);
        // Menu references are code-based, unlike retained grants. Never implicitly reconnect one.
        if (permissions.countMenuReferences(code) > 0) forbidden();
        var permission = new PermissionEntity();
        permission.setCode(code);
        permission.setName(request.name().strip());
        permission.setModule(request.module().strip());
        permission.setResourceType(request.type());
        permission.setStatus("DISABLED");
        permission.setSystemPermission(false);
        permission.setVersion(0);
        permission.setDeleted(false);
        permission.setCreatedAt(OffsetDateTime.now());
        permission.setUpdatedAt(permission.getCreatedAt());
        permission.setCreatedBy(current.username());
        permission.setUpdatedBy(current.username());
        permissions.insert(permission);
        var result = converter.toResponse(permission);
        audit(permission.getId(), current, "PERMISSION_CREATE", null, result);
        return result;
    }

    @Transactional
    public PermissionResponse update(long id, UpdatePermissionRequest request, Authentication actor) {
        users.lockSecurityWrites();
        var permission = locked(id, request.version());
        var current = currentActor(actor, "update");
        if (!normalize(request.permissionCode()).equals(permission.getCode())) forbidden();
        if (reserved(permission) && !current.administrator()) forbidden();
        var before = converter.toResponse(permission);
        permission.setName(request.name().strip());
        permission.setResourceType(request.type());
        permission.setModule(request.module().strip());
        save(permission, current);
        var result = detail(id);
        audit(id, current, "PERMISSION_UPDATE", before, result);
        return result;
    }

    @Transactional
    public PermissionResponse changeStatus(long id, PermissionStatusRequest request, Authentication actor) {
        users.lockSecurityWrites();
        var permission = locked(id, request.version());
        var current = currentActor(actor, "status");
        boolean changed = !Objects.equals(permission.getStatus(), request.status());
        if (reserved(permission) && (!current.administrator() || "DISABLED".equals(request.status()))) forbidden();
        // Grant-free activation confers no authority. Disabled and unassigned roles still retain grants.
        if (changed && "ENABLED".equals(request.status()) && !current.administrator()
                && permissions.countRetainedRoleGrants(id) > 0
                && !current.permissions().contains(permission.getCode())) forbidden();
        if ("DISABLED".equals(request.status())) protectOwn(permission, current);
        var before = converter.toResponse(permission);
        permission.setStatus(request.status());
        save(permission, current);
        if (changed) invalidateAffected(id);
        var result = detail(id);
        audit(id, current, "PERMISSION_STATUS", before, result);
        return result;
    }

    @Transactional
    public void delete(long id, int version, Authentication actor) {
        users.lockSecurityWrites();
        var permission = locked(id, version);
        var current = currentActor(actor, "delete");
        if (reserved(permission)) forbidden();
        protectOwn(permission, current);
        if (permissions.countMenuReferences(permission.getCode()) > 0)
            throw new BusinessException(SystemErrorCodes.PERMISSION_IN_USE);
        var before = converter.toResponse(permission);
        permission.setDeleted(true);
        save(permission, current);
        invalidateAffected(id);
        audit(id, current, "PERMISSION_DELETE", before, null);
    }

    private CurrentActor currentActor(Authentication actor, String operation) {
        if (actor == null || !actor.isAuthenticated() || !(actor.getPrincipal() instanceof SystemUserDetails))
            forbidden();
        var principal = ((SystemUserDetails) actor.getPrincipal()).getUser();
        var current = users.selectById(principal.userId());
        if (current == null || !"ENABLED".equals(current.getStatus())
                || !Objects.equals(current.getAuthVersion(), principal.authVersion())
                || (current.getLockedUntil() != null && current.getLockedUntil().isAfter(OffsetDateTime.now())))
            forbidden();
        var codes = users.selectEnabledPermissionCodes(current.getId());
        boolean admin = Boolean.TRUE.equals(current.getAdministrator());
        if (!admin && !codes.contains("system:permission:" + operation)) forbidden();
        return new CurrentActor(current.getId(), current.getUsername(), admin, codes);
    }

    private void protectOwn(PermissionEntity permission, CurrentActor actor) {
        if (!actor.administrator() && "ENABLED".equals(permission.getStatus()) && actor.permissions().contains(permission.getCode()))
            forbidden();
    }

    private boolean reserved(PermissionEntity permission) {
        return AuthorityRules.isReservedPermission(permission.getCode(), Boolean.TRUE.equals(permission.getSystemPermission()));
    }

    private void invalidateAffected(long id) {
        var affected = permissions.lockAffectedUsers(id);
        if (affected.isEmpty()) return;
        permissions.incrementAuthVersions(id);
        List<String> usernames = affected.stream().map(u -> u.getUsername()).toList();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String username : usernames) {
                    try {
                        sessions.invalidateAll(username);
                    } catch (RuntimeException failure) {
                        log.warn("Session invalidation failed after permission update; account version checks remain active");
                    }
                }
            }
        });
    }

    private PermissionEntity locked(long id, int version) {
        var permission = require(permissions.lockActive(id));
        if (!Objects.equals(permission.getVersion(), version)) throw new BusinessException(ErrorCode.DATA_CONFLICT);
        return permission;
    }

    private PermissionEntity require(PermissionEntity permission) {
        if (permission == null) throw new BusinessException(SystemErrorCodes.PERMISSION_NOT_FOUND);
        return permission;
    }

    private String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private void validateCode(String code) {
        if (code.length() > 160 || !code.matches("[a-z0-9][a-z0-9._-]*(?::[a-z0-9][a-z0-9._-]*)+"))
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }

    private void forbidden() {
        throw new BusinessException(SystemErrorCodes.PROTECTED_PERMISSION);
    }

    private void save(PermissionEntity permission, CurrentActor actor) {
        permission.setUpdatedBy(actor.username());
        if (permissions.updatePermission(permission) != 1) throw new BusinessException(ErrorCode.DATA_CONFLICT);
    }

    private void audit(long id, CurrentActor actor, String operation, PermissionResponse before, PermissionResponse after) {
        try {
            permissions.insertAudit(id, actor.id(), actor.username(), operation, json.writeValueAsString(before), json.writeValueAsString(after));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot serialize permission audit", failure);
        }
    }

    private record CurrentActor(long id, String username, boolean administrator, List<String> permissions) {
    }
}
