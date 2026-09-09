package com.xianshuyuan.scm.system.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record ReplaceRoleMenusRequest(
        @NotNull @Size(max=100) List<@NotNull @Positive Long> menuIds,
        @NotNull @PositiveOrZero Integer version) {}
