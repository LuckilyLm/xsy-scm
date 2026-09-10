package com.xianshuyuan.scm.auth.dto;

import com.xianshuyuan.scm.auth.validation.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @PasswordPolicy String newPassword,
        @NotNull @PositiveOrZero Integer version
) {
}
