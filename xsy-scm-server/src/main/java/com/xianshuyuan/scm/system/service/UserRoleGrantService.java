package com.xianshuyuan.scm.system.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianshuyuan.scm.auth.security.SystemUserDetails;
import com.xianshuyuan.scm.auth.service.UserSessionService;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.common.exception.ErrorCode;
import com.xianshuyuan.scm.system.SystemErrorCodes;
import com.xianshuyuan.scm.system.dto.*;
import com.xianshuyuan.scm.system.entity.SystemUserEntity;
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
public class UserRoleGrantService {
    private final SystemUserMapper users;
    private final RoleMapper roles;
    private final UserRoleGrantMapper grants;
    private final UserSessionService sessions;
    private final ObjectMapper json;

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public UserRolesResponse read(long id) {
        var user = require(users.selectById(id));
        return response(user);
    }

    @Transactional
    public UserRolesResponse replace(long id, ReplaceUserRolesRequest request, Authentication authentication) {
        users.lockSecurityWrites();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SystemUserDetails)) forbidden();
        var principal = ((SystemUserDetails) authentication.getPrincipal()).getUser();
        var requested = new TreeSet<>(request.roleIds());
        if (requested.size() != request.roleIds().size()) invalid();
        for (long roleId : requested) {
            var role = roles.lockActive(roleId);
            if (role == null) throw new BusinessException(SystemErrorCodes.ROLE_NOT_FOUND);
            if (!"ENABLED".equals(role.getStatus())) invalid();
        }
        var locked = new HashMap<Long, SystemUserEntity>();
        for (long userId : new TreeSet<>(List.of(id, principal.userId())))
            locked.put(userId, users.lockActiveUser(userId));
        var actor = locked.get(principal.userId());
        if (actor == null || !"ENABLED".equals(actor.getStatus())
                || !Objects.equals(actor.getAuthVersion(), principal.authVersion())
                || (actor.getLockedUntil() != null && actor.getLockedUntil().isAfter(OffsetDateTime.now())))
            forbidden();
        boolean administrator = Boolean.TRUE.equals(actor.getAdministrator());
        var actorPermissions = users.selectEnabledPermissionCodes(actor.getId());
        if (!administrator && !actorPermissions.contains("system:user:assign-roles")) forbidden();
        var target = require(locked.get(id));
        if (!Objects.equals(target.getVersion(), request.version()))
            throw new BusinessException(ErrorCode.DATA_CONFLICT);
        if (!administrator) {
            for (var role : grants.roles(id)) {
                if (!requested.contains(role.getId()) && com.xianshuyuan.scm.auth.security.AuthorityRules.isReservedRole(role.getCode(), Boolean.TRUE.equals(role.getSystemRole())))
                    forbidden();
            }
            if (Boolean.TRUE.equals(target.getAdministrator())
                    || Set.of("admin", "administrator", "system", "root").contains(target.getUsername().toLowerCase(Locale.ROOT)))
                forbidden();
            for (long roleId : requested) {
                var role = roles.selectById(roleId);
                if (com.xianshuyuan.scm.auth.security.AuthorityRules.isReservedRole(role.getCode(), Boolean.TRUE.equals(role.getSystemRole())))
                    forbidden();
            }
            var finalPermissions = requested.isEmpty() ? List.<String>of() : grants.permissions(new ArrayList<>(requested));
            if (!actorPermissions.containsAll(finalPermissions)) forbidden();
            var actorRoles = grants.roles(actor.getId()).stream()
                    .filter(r -> !Boolean.TRUE.equals(r.getDeleted()) && "ENABLED".equals(r.getStatus())).map(r -> r.getId()).toList();
            var actorMenus = actorRoles.isEmpty() ? List.<Long>of() : grants.menus(actorRoles);
            if (!requested.isEmpty() && !actorMenus.containsAll(grants.menus(new ArrayList<>(requested)))) forbidden();
            if (id == actor.getId() && !finalPermissions.containsAll(actorPermissions)) forbidden();
        }
        var before = response(target);
        var retained = new TreeSet<Long>();
        before.roles().forEach(r -> retained.add(r.id()));
        if (retained.equals(requested)) return before;
        for (long roleId : retained) if (!requested.contains(roleId)) grants.remove(id, roleId);
        for (long roleId : requested) if (!retained.contains(roleId)) grants.add(id, roleId, actor.getUsername());
        if (grants.incrementVersion(id, request.version(), actor.getUsername()) != 1)
            throw new BusinessException(ErrorCode.DATA_CONFLICT);
        var after = response(require(users.selectById(id)));
        try {
            users.insertAudit(id, actor.getId(), actor.getUsername(), "USER_ASSIGN_ROLES", json.writeValueAsString(before), json.writeValueAsString(after));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot serialize user role audit", failure);
        }
        String username = target.getUsername();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    sessions.invalidateAll(username);
                } catch (RuntimeException failure) {
                    log.warn("Session invalidation failed after user role update; account version checks remain active");
                }
            }
        });
        return after;
    }

    private UserRolesResponse response(SystemUserEntity user) {
        return new UserRolesResponse(user.getId(), user.getVersion(), grants.roles(user.getId()).stream()
                .map(r -> new UserRolesResponse.Role(r.getId(), r.getCode(), r.getName(), r.getStatus())).toList());
    }

    private SystemUserEntity require(SystemUserEntity user) {
        if (user == null) throw new BusinessException(SystemErrorCodes.USER_NOT_FOUND);
        return user;
    }

    private void invalid() {
        throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }

    private void forbidden() {
        throw new BusinessException(SystemErrorCodes.PROTECTED_USER);
    }
}
