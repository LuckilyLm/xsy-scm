package com.xianshuyuan.scm.system.dto;

import com.xianshuyuan.scm.auth.validation.PasswordPolicy;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ResetPasswordRequest(
        @PasswordPolicy String newPassword,
        @NotNull @PositiveOrZero Integer version
) {}
