package com.xianshuyuan.scm.system.dto;

import java.util.List;

public record RolePermissionsResponse(Long roleId, Integer version, List<Permission> permissions) {
    public record Permission(Long id, String code, String name, String status) {}
}
