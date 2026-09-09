package com.xianshuyuan.scm.system.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record ReplaceUserRolesRequest(@NotNull @Size(max=100) List<@NotNull @Positive Long> roleIds,
                                      @NotNull @PositiveOrZero Integer version) {}
