package com.xianshuyuan.scm.system.dto;

import java.time.OffsetDateTime;

public record DepartmentResponse(Long id, Long parentId, String code, String name,
        Integer sortOrder, String status, Integer version, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
