package com.xianshuyuan.scm.system.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianshuyuan.scm.auth.security.SystemUserDetails;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.common.exception.ErrorCode;
import com.xianshuyuan.scm.system.SystemErrorCodes;
import com.xianshuyuan.scm.system.dto.*;
import com.xianshuyuan.scm.system.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class RoleMenuGrantService {
    private final RoleMapper roles;
    private final RoleMenuGrantMapper grants;
    private final MenuMapper menus;
    private final SystemUserMapper users;
    private final ObjectMapper json;
    private final com.xianshuyuan.scm.auth.service.UserSessionService sessions;

    @Transactional
    public RoleMenusResponse replace(long id, ReplaceRoleMenusRequest request, Authentication authentication) {
        users.lockSecurityWrites();
        var role = roles.lockActive(id);
        if (role == null) throw new BusinessException(SystemErrorCodes.ROLE_NOT_FOUND);
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof SystemUserDetails details))
            throw new BusinessException(SystemErrorCodes.PROTECTED_ROLE);
        var requested = new TreeSet<>(request.menuIds());
        if (requested.size() != request.menuIds().size()) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        for (long menuId : requested) {
            var menu = menus.lockActive(menuId);
            if (menu == null) throw new BusinessException(SystemErrorCodes.MENU_NOT_FOUND);
            if (!"ENABLED".equals(menu.getStatus())) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        var locked = grants.lockActorAndAffectedUsers(id, details.getUser().userId());
        var actor = locked.stream().filter(u -> u.getId().equals(details.getUser().userId())).findFirst().orElse(null);
        if (actor == null || !"ENABLED".equals(actor.getStatus())
                || !Objects.equals(actor.getAuthVersion(), details.getUser().authVersion())
                || (actor.getLockedUntil() != null && actor.getLockedUntil().isAfter(OffsetDateTime.now())))
            throw new BusinessException(SystemErrorCodes.PROTECTED_ROLE);
        if (!Boolean.TRUE.equals(actor.getAdministrator())
                && !users.selectEnabledPermissionCodes(actor.getId()).contains("system:role:assign-menus"))
            throw new BusinessException(SystemErrorCodes.PROTECTED_ROLE);
        if (!Boolean.TRUE.equals(actor.getAdministrator())) {
            if (com.xianshuyuan.scm.auth.security.AuthorityRules.isReservedRole(role.getCode(), Boolean.TRUE.equals(role.getSystemRole()))
                    || !grants.actorMenuIds(actor.getId()).containsAll(requested))
                throw new BusinessException(SystemErrorCodes.PROTECTED_ROLE);
            for (long menuId : requested) {
                if (!Boolean.TRUE.equals(menus.selectById(menuId).getVisible()))
                    throw new BusinessException(SystemErrorCodes.PROTECTED_ROLE);
            }
        }
        var actorPermissions = users.selectEnabledPermissionCodes(actor.getId());
        for (long menuId : requested) {
            var node = menus.selectById(menuId);
            var visited = new HashSet<Long>();
            while (node != null) {
                if (!visited.add(node.getId())) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
                if (!Boolean.TRUE.equals(actor.getAdministrator())
                        && (!Boolean.TRUE.equals(node.getVisible()) || !"ENABLED".equals(node.getStatus())
                        || (node.getRequiredPermissionCode() != null && !actorPermissions.contains(node.getRequiredPermissionCode()))))
                    throw new BusinessException(SystemErrorCodes.PROTECTED_ROLE);
                if (node.getParentId() == null) break;
                node = menus.selectById(node.getParentId());
                if (node == null || !"DIRECTORY".equals(node.getType()))
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
        }
        if (!Objects.equals(role.getVersion(), request.version())) throw new BusinessException(ErrorCode.DATA_CONFLICT);
        var before = read(id);
        var retained = new TreeSet<>(before.menus().stream().map(RoleMenusResponse.Menu::id).toList());
        if (retained.equals(requested)) return before;
        for (long menuId : retained) if (!requested.contains(menuId)) grants.remove(id, menuId);
        for (long menuId : requested) if (!retained.contains(menuId)) grants.add(id, menuId, actor.getUsername());
        if (grants.incrementVersion(id, request.version(), actor.getUsername()) != 1) throw new BusinessException(ErrorCode.DATA_CONFLICT);
        roles.incrementAuthVersions(id);
        var after = read(id);
        try {
            roles.insertAudit(id, actor.getId(), actor.getUsername(), "ROLE_ASSIGN_MENUS", json.writeValueAsString(before), json.writeValueAsString(after));
        } catch (JsonProcessingException failure) { throw new IllegalStateException("Cannot serialize role menu audit", failure); }
        var affectedNames = roles.lockAffectedUsers(id).stream().map(com.xianshuyuan.scm.system.entity.SystemUserEntity::getUsername).toList();
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
            @Override public void afterCommit() {
                for (String username : affectedNames) {
                    try { sessions.invalidateAll(username); }
                    catch (RuntimeException failure) { log.warn("Session invalidation failed after role menu update; account version checks remain active"); }
                }
            }
        });
        return after;
    }

    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public RoleMenusResponse read(long id) {
        var role = roles.selectById(id);
        if (role == null) throw new BusinessException(SystemErrorCodes.ROLE_NOT_FOUND);
        return new RoleMenusResponse(role.getId(), role.getVersion(), grants.menus(id).stream()
                .map(m -> new RoleMenusResponse.Menu(m.getId(), m.getParentId(), m.getType(), m.getName(),
                        m.getStatus(), m.getVisible(), m.getRequiredPermissionCode())).toList());
    }
}
