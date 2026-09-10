package com.xianshuyuan.scm.system.dto;

import jakarta.validation.constraints.*;

public record CreateMenuRequest(
        @NotNull @Pattern(regexp = "DIRECTORY|MENU") String type,
        @Positive Long parentId,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 120) String routeKey,
        @Size(max = 240) String path,
        @Size(max = 64) String icon,
        @Size(max = 160) String requiredPermission,
        @NotNull @PositiveOrZero Integer sort,
        @NotNull Boolean visible,
        @NotNull @Pattern(regexp = "ENABLED|DISABLED") String status) {
}
