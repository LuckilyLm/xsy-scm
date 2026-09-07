package com.xianshuyuan.scm.supplier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WarehouseSaveRequest(
        @NotBlank @Size(max = 64) String warehouseCode,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 500) String address,
        @Size(max = 500) String remark,
        Integer version
) {
}
