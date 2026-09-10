package com.xianshuyuan.scm.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianshuyuan.scm.auth.security.SystemUserDetails;
import com.xianshuyuan.scm.auth.service.UserSessionService;
import com.xianshuyuan.scm.common.api.PageData;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.common.exception.ErrorCode;
import com.xianshuyuan.scm.system.SystemErrorCodes;
import com.xianshuyuan.scm.system.converter.MenuConverter;
import com.xianshuyuan.scm.system.dto.*;
import com.xianshuyuan.scm.system.entity.MenuEntity;
import com.xianshuyuan.scm.system.mapper.MenuMapper;
import com.xianshuyuan.scm.system.mapper.SystemUserMapper;
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
public class MenuService {
    // Keys identify implemented list routes, never module/component import paths.
    private static final Map<String, String> ROUTES = Map.ofEntries(
            Map.entry("products", "/products"), Map.entry("customers", "/customers"),
            Map.entry("customer-agreement-prices", "/customer-agreement-prices"),
            Map.entry("orders", "/orders"), Map.entry("order-returns", "/order-returns"),
            Map.entry("order-refunds", "/order-refunds"), Map.entry("purchase-demands", "/purchases/demands"),
            Map.entry("purchase-orders", "/purchases/orders"), Map.entry("purchase-receipts", "/purchases/receipts"),
            Map.entry("suppliers", "/warehouses/suppliers"), Map.entry("warehouses", "/warehouses/settings"),
            Map.entry("inventories", "/warehouses/inventories"), Map.entry("inventory-movements", "/warehouses/inventory-movements"));
    private final MenuMapper menus;
    private final SystemUserMapper users;
    private final MenuConverter converter;
    private final UserSessionService sessions;
    private final ObjectMapper json;

    public PageData<MenuResponse> list(MenuQuery query) {
        var wrapper = new LambdaQueryWrapper<MenuEntity>();
        wrapper.like(query.getKeyword() != null && !query.getKeyword().isBlank(), MenuEntity::getName,
                query.getKeyword() == null ? null : query.getKeyword().strip());
        wrapper.eq(query.getType() != null, MenuEntity::getType, query.getType());
        wrapper.eq(query.getStatus() != null, MenuEntity::getStatus, query.getStatus());
        wrapper.eq(query.getVisible() != null, MenuEntity::getVisible, query.getVisible());
        wrapper.eq(query.getParentId() != null, MenuEntity::getParentId, query.getParentId());
        wrapper.orderByAsc(MenuEntity::getSortOrder, MenuEntity::getId);
        var page = menus.selectPage(new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return new PageData<>(page.getRecords().stream().map(converter::toResponse).toList(), page.getCurrent(), page.getSize(), page.getTotal());
    }

    public MenuResponse detail(long id) {
        return converter.toResponse(require(menus.selectById(id)));
    }

    public List<MenuResponse> tree(MenuTreeQuery query) {
        var all = menus.selectList(new LambdaQueryWrapper<MenuEntity>().orderByAsc(MenuEntity::getSortOrder, MenuEntity::getId));
        var children = children(all);
        var ordered = new ArrayList<MenuEntity>();
        var queue = new ArrayDeque<MenuEntity>(children.getOrDefault(null, List.of()));
        var depths = new HashMap<Long, Integer>();
        while (!queue.isEmpty()) {
            var node = queue.remove();
            int depth = node.getParentId() == null ? 1 : depths.get(node.getParentId()) + 1;
            if (depth > MAX_DEPTH) tooDeep();
            depths.put(node.getId(), depth);
            ordered.add(node);
            queue.addAll(children.getOrDefault(node.getId(), List.of()));
        }
        if (ordered.size() != all.size()) invalid();
        // Validate the entire topology before filters, and build bottom-up without recursion.
        var responses = new HashMap<Long, MenuResponse>();
        for (int i = ordered.size() - 1; i >= 0; i--) {
            var node = ordered.get(i);
            if (query.getStatus() != null && !query.getStatus().equals(node.getStatus())) continue;
            if (query.getVisible() != null && !query.getVisible().equals(node.getVisible())) continue;
            var nested = children.getOrDefault(node.getId(), List.of()).stream().map(n -> responses.get(n.getId())).filter(Objects::nonNull).toList();
            responses.put(node.getId(), converter.toResponse(node).withChildren(nested));
        }
        return children.getOrDefault(null, List.of()).stream().map(n -> responses.get(n.getId())).filter(Objects::nonNull).toList();
    }

    // Implementation envelope for bounded JSON nesting, not a business taxonomy requirement.
    private static final int MAX_DEPTH = 64;

    private Map<Long, List<MenuEntity>> children(List<MenuEntity> all) {
        Map<Long, List<MenuEntity>> result = new HashMap<>();
        for (var node : all) result.computeIfAbsent(node.getParentId(), ignored -> new ArrayList<>()).add(node);
        return result;
    }

    private void validateDepth(MenuEntity menu, Long parent, int ancestorDepth) {
        // Unchanged legacy placement remains editable; list/detail and reparenting permit repair.
        if (menu.getId() != null && Objects.equals(menu.getParentId(), parent)) return;
        int height = 1;
        if (menu.getId() != null) {
            var graph = children(menus.selectList(new LambdaQueryWrapper<MenuEntity>()));
            var level = List.of(menu);
            Set<Long> visited = new HashSet<>();
            while (!level.isEmpty()) {
                List<MenuEntity> next = new ArrayList<>();
                for (var node : level) {
                    if (!visited.add(node.getId())) invalid();
                    next.addAll(graph.getOrDefault(node.getId(), List.of()));
                }
                if (next.isEmpty()) break;
                height++;
                level = next;
            }
        }
        if (ancestorDepth + height > MAX_DEPTH) tooDeep();
    }

    private void tooDeep() {
        throw new BusinessException(SystemErrorCodes.MENU_TOO_DEEP);
    }

    @Transactional
    public MenuResponse create(CreateMenuRequest request, Authentication actor) {
        users.lockSecurityWrites();
        var current = currentActor(actor, "create");
        var menu = new MenuEntity();
        apply(menu, request);
        menu.setVersion(0);
        menu.setDeleted(false);
        menu.setCreatedAt(OffsetDateTime.now());
        menu.setUpdatedAt(menu.getCreatedAt());
        menu.setCreatedBy(current.username());
        menu.setUpdatedBy(current.username());
        menus.insert(menu);
        var result = converter.toResponse(menu);
        audit(menu.getId(), current, "MENU_CREATE", null, result);
        return result;
    }

    @Transactional
    public MenuResponse update(long id, UpdateMenuRequest request, Authentication actor) {
        users.lockSecurityWrites();
        var menu = locked(id, request.version());
        var current = currentActor(actor, "update");
        var before = converter.toResponse(menu);
        apply(menu, request.values());
        protectExpansion(menu, before, current);
        invalidateAffected(id);
        save(menu, current);
        var result = detail(id);
        audit(id, current, "MENU_UPDATE", before, result);
        return result;
    }

    @Transactional
    public MenuResponse changeStatus(long id, MenuStatusRequest request, Authentication actor) {
        users.lockSecurityWrites();
        var menu = locked(id, request.version());
        var current = currentActor(actor, "status");
        var before = converter.toResponse(menu);
        menu.setStatus(request.status());
        protectExpansion(menu, before, current);
        if (!Objects.equals(before.status(), request.status())) invalidateAffected(id);
        save(menu, current);
        var result = detail(id);
        audit(id, current, "MENU_STATUS", before, result);
        return result;
    }

    @Transactional
    public void delete(long id, int version, Authentication actor) {
        users.lockSecurityWrites();
        var menu = locked(id, version);
        var current = currentActor(actor, "delete");
        if (menus.countChildren(id) > 0 || menus.countLiveRoles(id) > 0)
            throw new BusinessException(SystemErrorCodes.MENU_IN_USE);
        var before = converter.toResponse(menu);
        menu.setDeleted(true);
        save(menu, current);
        audit(id, current, "MENU_DELETE", before, null);
    }

    private void apply(MenuEntity menu, CreateMenuRequest request) {
        String route = clean(request.routeKey()), path = clean(request.path()), icon = clean(request.icon());
        String permission = clean(request.requiredPermission());
        if ("MENU".equals(request.type())) {
            if (route == null || !Objects.equals(ROUTES.get(route), path) || path == null) invalid();
            if (menu.getId() != null && menus.countChildren(menu.getId()) > 0) invalid();
            if (menus.countRoute(route, menu.getId()) > 0) throw new BusinessException(ErrorCode.DATA_CONFLICT);
        } else if (route != null || path != null) invalid();
        if (icon != null && !icon.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")) invalid();
        if (permission != null && menus.countPermission(permission) != 1) invalid();
        Long parent = request.parentId();
        Set<Long> visited = new HashSet<>();
        while (parent != null) {
            if (Objects.equals(parent, menu.getId()) || !visited.add(parent)) invalid();
            var node = menus.selectById(parent);
            if (node == null || !"DIRECTORY".equals(node.getType())) invalid();
            parent = node.getParentId();
        }
        validateDepth(menu, request.parentId(), visited.size());
        menu.setParentId(request.parentId());
        menu.setType(request.type());
        menu.setName(request.name().strip());
        menu.setRouteKey(route);
        menu.setPath(path);
        menu.setIconKey(icon);
        menu.setRequiredPermissionCode(permission);
        menu.setSortOrder(request.sort());
        menu.setVisible(request.visible());
        menu.setStatus(request.status());
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
        if (!admin && !codes.contains("system:menu:" + operation)) forbidden();
        return new CurrentActor(current.getId(), current.getUsername(), admin, codes);
    }

    private void protectExpansion(MenuEntity menu, MenuResponse before, CurrentActor actor) {
        if (actor.administrator() || menus.countLiveRoles(menu.getId()) == 0) return;
        // Retained navigation grants include disabled roles and descendants. Changing their
        // destination or removing a gate is a grant-like operation, not ordinary metadata.
        boolean identityChanged = !Objects.equals(before.parentId(), menu.getParentId())
                || !Objects.equals(before.type(), menu.getType())
                || !Objects.equals(before.routeKey(), menu.getRouteKey())
                || !Objects.equals(before.path(), menu.getPath())
                || (before.requiredPermission() != null
                && !Objects.equals(before.requiredPermission(), menu.getRequiredPermissionCode()));
        boolean activated = (!"ENABLED".equals(before.status()) && "ENABLED".equals(menu.getStatus()))
                || (!Boolean.TRUE.equals(before.visible()) && Boolean.TRUE.equals(menu.getVisible()));
        if (identityChanged || activated) forbidden();
    }

    private void invalidateAffected(long id) {
        var affected = menus.lockAffectedUsers(id);
        if (affected.isEmpty()) return;
        menus.incrementAuthVersions(affected.stream().map(u -> u.getId()).toList());
        List<String> names = affected.stream().map(u -> u.getUsername()).toList();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String name : names) {
                    try {
                        sessions.invalidateAll(name);
                    } catch (RuntimeException failure) {
                        log.warn("Session invalidation failed after menu update; account version checks remain active");
                    }
                }
            }
        });
    }

    private MenuEntity locked(long id, int version) {
        var menu = require(menus.lockActive(id));
        if (!Objects.equals(menu.getVersion(), version)) throw new BusinessException(ErrorCode.DATA_CONFLICT);
        return menu;
    }

    private MenuEntity require(MenuEntity menu) {
        if (menu == null) throw new BusinessException(SystemErrorCodes.MENU_NOT_FOUND);
        return menu;
    }

    private void save(MenuEntity menu, CurrentActor actor) {
        menu.setUpdatedBy(actor.username());
        if (menus.updateMenu(menu) != 1) throw new BusinessException(ErrorCode.DATA_CONFLICT);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private void invalid() {
        throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }

    private void forbidden() {
        throw new BusinessException(SystemErrorCodes.PROTECTED_MENU);
    }

    private void audit(long id, CurrentActor actor, String operation, MenuResponse before, MenuResponse after) {
        try {
            menus.insertAudit(id, actor.id(), actor.username(), operation, json.writeValueAsString(before), json.writeValueAsString(after));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot serialize menu audit", failure);
        }
    }

    private record CurrentActor(long id, String username, boolean administrator, List<String> permissions) {
    }
}
