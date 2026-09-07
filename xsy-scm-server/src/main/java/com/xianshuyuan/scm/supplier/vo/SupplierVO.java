package com.xianshuyuan.scm.supplier.vo;

import com.xianshuyuan.scm.customer.entity.EnabledStatus;

public record SupplierVO(
        Long id,
        String supplierCode,
        String name,
        EnabledStatus status,
        Integer version,
        String remark
) {
}
