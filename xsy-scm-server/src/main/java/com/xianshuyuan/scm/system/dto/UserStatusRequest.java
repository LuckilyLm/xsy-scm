package com.xianshuyuan.scm.system.dto;
import jakarta.validation.constraints.*;
public record UserStatusRequest(@NotNull @Pattern(regexp="ENABLED|DISABLED") String status,
        @NotNull @PositiveOrZero Integer version) {}
