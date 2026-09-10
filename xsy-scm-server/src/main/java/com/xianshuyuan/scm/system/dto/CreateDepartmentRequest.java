package com.xianshuyuan.scm.system.dto;

import jakarta.validation.constraints.*;

public record CreateDepartmentRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 100) String name,
        @Positive Long parentId,
        @NotNull @PositiveOrZero Integer sortOrder
) {
}
