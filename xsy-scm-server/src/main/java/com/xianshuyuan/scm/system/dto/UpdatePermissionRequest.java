package com.xianshuyuan.scm.system.dto;

import jakarta.validation.constraints.*;

public record UpdatePermissionRequest(
        @NotBlank @Size(max = 160) String permissionCode,
        @NotBlank @Size(max = 100) String name,
        @NotNull @Pattern(regexp = "PAGE|ACTION|API") String type,
        @NotBlank @Size(max = 64) String module,
        @NotNull @PositiveOrZero Integer version
) {
}
