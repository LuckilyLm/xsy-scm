package com.xianshuyuan.scm.system.dto;

import java.time.OffsetDateTime;

public record PermissionResponse(Long id, String permissionCode, String name, String type,
                                 String module, String status, Boolean systemPermission,
                                 Integer version, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
