package com.xianshuyuan.scm.customer.dto;

import com.xianshuyuan.scm.customer.entity.EnabledStatus;
import jakarta.validation.constraints.*;

public record CustomerStatusRequest(@NotNull @Min(0) Integer version, @NotNull EnabledStatus status) {
}
