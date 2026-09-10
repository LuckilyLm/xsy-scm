package com.xianshuyuan.scm.system.dto;

import java.time.OffsetDateTime;

public record RoleResponse(Long id, String roleCode, String name, String description, String status,
                           Boolean systemRole, Integer version, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}
