package com.xianshuyuan.scm.supplier.dto;

import com.xianshuyuan.scm.customer.entity.EnabledStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MasterDataStatusRequest(
        @NotNull @Min(0) Integer version,
        @NotNull EnabledStatus status
) {
}
