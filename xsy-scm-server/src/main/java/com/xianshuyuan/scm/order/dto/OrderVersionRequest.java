package com.xianshuyuan.scm.order.dto;

import jakarta.validation.constraints.*;

public record OrderVersionRequest(
        @NotNull(message = "版本号不能为空") @Min(value = 0, message = "版本号不正确") Integer version) {
}
