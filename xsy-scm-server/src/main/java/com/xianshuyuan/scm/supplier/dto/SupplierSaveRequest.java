package com.xianshuyuan.scm.supplier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierSaveRequest(@NotBlank @Size(max = 64) String supplierCode, @NotBlank @Size(max = 150) String name,
                                  @Size(max = 500) String remark, Integer version) {
}
