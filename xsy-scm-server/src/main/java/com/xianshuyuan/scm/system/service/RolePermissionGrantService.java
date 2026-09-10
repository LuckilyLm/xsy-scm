package com.xianshuyuan.scm.system.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianshuyuan.scm.auth.security.AuthorityRules;
import com.xianshuyuan.scm.auth.security.SystemUserDetails;
import com.xianshuyuan.scm.auth.service.UserSessionService;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.common.exception.ErrorCode;
import com.xianshuyuan.scm.system.SystemErrorCodes;
import com.xianshuyuan.scm.system.dto.*;
import com.xianshuyuan.scm.system.entity.RoleEntity;
import com.xianshuyuan.scm.system.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class RolePermissionGrantService {
    private final SystemUserMapper users;
    private final RoleMapper roles;
    private final PermissionMapper permissions;
    private final RolePermissionGrantMapper grants;
    private final UserSessionService sessions;
    private final ObjectMapper json;

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public RolePermissionsResponse read(long id) {
        return response(require(roles.selectById(id)));
    }

    @Transactional
    public RolePermissionsResponse replace(long id, ReplaceRolePermissionsRequest request, Authentication authentication) {
        users.lockSecurityWrites();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SystemUserDetails)) forbidden();
        var principal = ((SystemUserDetails) authentication.getPrincipal()).getUser();
        var role = require(roles.lockActive(id));
        var requested = new TreeSet<>(request.permissionIds());
        if (requested.size() != request.permissionIds().size()) invalid();
        var finalCodes = new HashSet<String>();
        for (long permissionId : requested) {
            var permission = permissions.lockActive(permissionId);
            if (permission == null) throw new BusinessException(SystemErrorCodes.PERMISSION_NOT_FOUND);
            if (!"ENABLED".equals(permission.getStatus())) invalid();
            finalCodes.add(permission.getCode());
        }
        var locked = grants.lockActorAndAffectedUsers(id, principal.userId());
        var actor = locked.stream().filter(u -> u.getId().equals(principal.userId())).findFirst().orElse(null);
        if (actor == null || !"ENABLED".equals(actor.getStatus())
                || !Objects.equals(actor.getAuthVersion(), principal.authVersion())
                || (actor.getLockedUntil() != null && actor.getLockedUntil().isAfter(OffsetDateTime.now())))
            forbidden();
        boolean administrator = Boolean.TRUE.equals(actor.getAdministrator());
        var actorCodes = users.selectEnabledPermissionCodes(actor.getId());
        if (!administrator && !actorCodes.contains("system:role:assign-permissions")) forbidden();
        if (!Objects.equals(role.getVersion(), request.version())) throw new BusinessException(ErrorCode.DATA_CONFLICT);
        // The administrator marker comes only from sys_user.administrator, never a role grant.
        if (finalCodes.contains("system.administrator")) forbidden();
        if (!administrator) {
            if (AuthorityRules.isReservedRole(role.getCode(), Boolean.TRUE.equals(role.getSystemRole()))) forbidden();
            if (!actorCodes.containsAll(finalCodes)) forbidden();
            if (!finalCodes.containsAll(roles.permissionsLost(id, actor.getId()))) forbidden();
        }
        var before = response(role);
        var retained = new TreeSet<Long>();
        before.permissions().forEach(p -> retained.add(p.id()));
        if (retained.equals(requested)) return before;
        for (long permissionId : retained) if (!requested.contains(permissionId)) grants.remove(id, permissionId);
        for (long permissionId : requested)
            if (!retained.contains(permissionId)) grants.add(id, permissionId, actor.getUsername());
        if (grants.incrementVersion(id, request.version(), actor.getUsername()) != 1)
            throw new BusinessException(ErrorCode.DATA_CONFLICT);
        var affected = roles.lockAffectedUsers(id);
        roles.incrementAuthVersions(id);
        var after = response(require(roles.selectById(id)));
        try {
            roles.insertAudit(id, actor.getId(), actor.getUsername(), "ROLE_ASSIGN_PERMISSIONS", json.writeValueAsString(before), json.writeValueAsString(after));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot serialize role permission audit", failure);
        }
        var usernames = affected.stream().map(u -> u.getUsername()).toList();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String username : usernames) {
                    try {
                        sessions.invalidateAll(username);
                    } catch (RuntimeException failure) {
                        log.warn("Session invalidation failed after role permission update; account version checks remain active");
                    }
                }
            }
        });
        return after;
    }

    private RolePermissionsResponse response(RoleEntity role) {
        return new RolePermissionsResponse(role.getId(), role.getVersion(), grants.permissions(role.getId()).stream()
                .map(p -> new RolePermissionsResponse.Permission(p.getId(), p.getCode(), p.getName(), p.getStatus())).toList());
    }

    private RoleEntity require(RoleEntity role) {
        if (role == null) throw new BusinessException(SystemErrorCodes.ROLE_NOT_FOUND);
        return role;
    }

    private void invalid() {
        throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }

    private void forbidden() {
        throw new BusinessException(SystemErrorCodes.PROTECTED_ROLE);
    }
}
