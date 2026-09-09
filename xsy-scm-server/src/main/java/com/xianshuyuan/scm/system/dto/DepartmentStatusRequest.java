package com.xianshuyuan.scm.system.dto;

import jakarta.validation.constraints.*;

public record DepartmentStatusRequest(
        @NotNull @Pattern(regexp = "ENABLED|DISABLED") String status,
        @NotNull @PositiveOrZero Integer version
) {}
