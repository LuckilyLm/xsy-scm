package com.xianshuyuan.scm.system.dto;

import java.util.List;

public record RoleMenusResponse(Long roleId, Integer version, List<Menu> menus) {
    public record Menu(Long id, Long parentId, String type, String name, String status,
                       Boolean visible, String requiredPermission) {
    }
}
