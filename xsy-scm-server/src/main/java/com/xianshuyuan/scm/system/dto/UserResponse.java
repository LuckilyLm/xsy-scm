package com.xianshuyuan.scm.system.dto;

import java.time.OffsetDateTime;

public record UserResponse(Long id, String username, String displayName, Long departmentId,
                           String email, String phone, String status, Integer version,
                           OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}
