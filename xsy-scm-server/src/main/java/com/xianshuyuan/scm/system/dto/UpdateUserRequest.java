package com.xianshuyuan.scm.system.dto;

import jakarta.validation.constraints.*;

public record UpdateUserRequest(@NotBlank @Size(max = 100) String displayName,
                                @Positive Long departmentId, @Email @Size(max = 254) String email,
                                @Size(max = 32) String phone, @NotNull @PositiveOrZero Integer version) {
}
