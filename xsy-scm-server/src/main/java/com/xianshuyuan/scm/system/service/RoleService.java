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
import com.xianshuyuan.scm.system.converter.RoleConverter;
import com.xianshuyuan.scm.system.dto.*;
import com.xianshuyuan.scm.system.entity.RoleEntity;
import com.xianshuyuan.scm.system.mapper.RoleMapper;
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
public class RoleService {
    private final RoleMapper roles;
    private final SystemUserMapper users;
    private final RoleConverter converter;
    private final UserSessionService sessions;
    private final ObjectMapper json;

    public PageData<RoleResponse> list(RoleQuery query) {
        var wrapper = new LambdaQueryWrapper<RoleEntity>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String keyword = query.getKeyword().strip();
            wrapper.and(w -> w.like(RoleEntity::getCode, keyword).or().like(RoleEntity::getName, keyword));
        }
        wrapper.eq(query.getStatus() != null, RoleEntity::getStatus, query.getStatus());
        wrapper.orderByAsc(RoleEntity::getId);
        var page = roles.selectPage(new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return new PageData<>(page.getRecords().stream().map(converter::toResponse).toList(),
                page.getCurrent(), page.getSize(), page.getTotal());
    }

    public RoleResponse detail(long id) {
        return converter.toResponse(require(roles.selectById(id)));
    }

    @Transactional
    public RoleResponse create(CreateRoleRequest request, Authentication actor) {
        users.lockSecurityWrites();
        String code = code(request.roleCode());
        // Reserved/security properties are never caller-controlled, even through admin CRUD.
        if (AuthorityRules.isReservedRole(code, false)) forbidden();
        unique(null, code);
        var role = new RoleEntity();
        role.setCode(code);
        role.setName(request.name().strip());
        role.setDescription(optional(request.description()));
        role.setStatus("ENABLED");
        role.setSystemRole(false);
        role.setVersion(0);
        role.setDeleted(false);
        role.setCreatedAt(OffsetDateTime.now());
        role.setUpdatedAt(role.getCreatedAt());
        role.setCreatedBy(actor.getName());
        role.setUpdatedBy(actor.getName());
        roles.insert(role);
        var result = converter.toResponse(role);
        audit(role.getId(), actor, "ROLE_CREATE", null, result);
        return result;
    }

    @Transactional
    public RoleResponse update(long id, UpdateRoleRequest request, Authentication actor) {
        users.lockSecurityWrites();
        var role = locked(id, request.version());
        protect(role, actor, false);
        String code = code(request.roleCode());
        if (!code.equals(role.getCode()) && AuthorityRules.isReservedRole(code, false)) forbidden();
        unique(id, code);
        var before = converter.toResponse(role);
        boolean identityChanged = !Objects.equals(role.getCode(), code) && "ENABLED".equals(role.getStatus());
        role.setCode(code);
        role.setName(request.name().strip());
        role.setDescription(optional(request.description()));
        save(role, actor);
        if (identityChanged) invalidateAffected(id);
        var result = detail(id);
        audit(id, actor, "ROLE_UPDATE", before, result);
        return result;
    }

    @Transactional
    public RoleResponse changeStatus(long id, RoleStatusRequest request, Authentication actor) {
        users.lockSecurityWrites();
        var role = locked(id, request.version());
        if ("DISABLED".equals(role.getStatus()) && "ENABLED".equals(request.status())) protectActivation(role, actor);
        protect(role, actor, "DISABLED".equals(request.status()));
        var before = converter.toResponse(role);
        boolean changed = !Objects.equals(role.getStatus(), request.status());
        role.setStatus(request.status());
        save(role, actor);
        if (changed) invalidateAffected(id);
        var result = detail(id);
        audit(id, actor, "ROLE_STATUS", before, result);
        return result;
    }

    @Transactional
    public void delete(long id, int version, Authentication actor) {
        users.lockSecurityWrites();
        var role = locked(id, version);
        protect(role, actor, true);
        var before = converter.toResponse(role);
        role.setDeleted(true);
        save(role, actor);
        invalidateAffected(id);
        audit(id, actor, "ROLE_DELETE", before, null);
    }

    private void protectActivation(RoleEntity role, Authentication actor) {
        // Enabling retained grants is an authority expansion, not merely a metadata change.
        // Re-read under the shared security lock; session authorities/admin flags can be stale.
        if (!(actor.getPrincipal() instanceof SystemUserDetails)) forbidden();
        var principal = ((SystemUserDetails) actor.getPrincipal()).getUser();
        var current = users.selectById(principal.userId());
        if (!actor.isAuthenticated() || current == null || !"ENABLED".equals(current.getStatus())
                || !Objects.equals(current.getAuthVersion(), principal.authVersion())
                || (current.getLockedUntil() != null && current.getLockedUntil().isAfter(OffsetDateTime.now())))
            forbidden();
        if (Boolean.TRUE.equals(current.getAdministrator())) return;
        if (AuthorityRules.isReservedRole(role.getCode(), Boolean.TRUE.equals(role.getSystemRole()))) forbidden();
        var permissions = users.selectEnabledPermissionCodes(current.getId());
        if (!permissions.contains("system:role:status")
                || !permissions.containsAll(roles.activatablePermissions(role.getId()))) forbidden();
    }

    private void protect(RoleEntity role, Authentication actor, boolean destructive) {
        if (AuthorityRules.isReservedRole(role.getCode(), Boolean.TRUE.equals(role.getSystemRole()))
                && (destructive || !AuthorityRules.isAdministrator(actor))) forbidden();
        if (destructive && actor.getPrincipal() instanceof SystemUserDetails details
                && !AuthorityRules.canRemoveOwnPermissions(actor, roles.permissionsLost(role.getId(), details.getUser().userId())))
            forbidden();
        // Administrators are sys_user.administrator, never role names. These commands do not
        // modify that flag, user usability, or the last-usable-administrator invariant.
    }

    private void invalidateAffected(long roleId) {
        var affected = roles.lockAffectedUsers(roleId);
        if (affected.isEmpty()) return;
        roles.incrementAuthVersions(roleId);
        List<String> usernames = affected.stream().map(u -> u.getUsername()).toList();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String username : usernames) {
                    try {
                        sessions.invalidateAll(username);
                    } catch (RuntimeException failure) {
                        // The commit succeeded. AccountVersionFilter rejects stale sessions even
                        // if the session repository fails; continue invalidating other users.
                        log.warn("Session invalidation failed after role update; account version checks remain active");
                    }
                }
            }
        });
    }

    private RoleEntity locked(long id, int version) {
        var role = require(roles.lockActive(id));
        if (!Objects.equals(role.getVersion(), version)) throw new BusinessException(ErrorCode.DATA_CONFLICT);
        return role;
    }

    private RoleEntity require(RoleEntity role) {
        if (role == null) throw new BusinessException(SystemErrorCodes.ROLE_NOT_FOUND);
        return role;
    }

    private void unique(Long id, String code) {
        if (roles.countCode(code, id) > 0) throw new BusinessException(ErrorCode.DATA_CONFLICT);
    }

    private String code(String value) {
        String code = value.strip().toLowerCase(Locale.ROOT);
        if (!code.matches("[a-z0-9][a-z0-9._-]{0,99}")) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        return code;
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private void forbidden() {
        throw new BusinessException(SystemErrorCodes.PROTECTED_ROLE);
    }

    private void save(RoleEntity role, Authentication actor) {
        role.setUpdatedBy(actor.getName());
        if (roles.updateRole(role) != 1) throw new BusinessException(ErrorCode.DATA_CONFLICT);
    }

    private void audit(long id, Authentication actor, String operation, RoleResponse before, RoleResponse after) {
        Long actorId = actor.getPrincipal() instanceof SystemUserDetails details ? details.getUser().userId() : null;
        try {
            roles.insertAudit(id, actorId, actor.getName(), operation, json.writeValueAsString(before), json.writeValueAsString(after));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot serialize role audit", failure);
        }
    }
}
