package com.xianshuyuan.scm.system.dto;

import java.util.List;

public record MenuResponse(Long id, Long parentId, String type, String name, String routeKey, String path,
                           String icon, String requiredPermission, Integer sort, Boolean visible,
                           String status, Integer version, List<MenuResponse> children) {
    public MenuResponse withChildren(List<MenuResponse> children) {
        return new MenuResponse(id, parentId, type, name, routeKey, path, icon, requiredPermission, sort, visible, status, version, children);
    }
}
