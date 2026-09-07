package com.xianshuyuan.scm.order.dto;

import jakarta.validation.constraints.*;

public record OrderReturnDecisionRequest(@NotNull @Min(0) Integer version, @NotBlank @Size(max = 500) String reason) {
}
