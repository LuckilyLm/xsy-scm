package com.xianshuyuan.scm.system.dto;

import jakarta.validation.constraints.*;

import java.util.List;

public record ReplaceRolePermissionsRequest(
        @NotNull @Size(max = 100) List<@NotNull @Positive Long> permissionIds,
        @NotNull @PositiveOrZero Integer version) {
}
