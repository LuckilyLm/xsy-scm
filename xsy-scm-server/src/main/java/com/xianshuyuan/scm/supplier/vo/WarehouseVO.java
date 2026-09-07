package com.xianshuyuan.scm.supplier.vo;

import com.xianshuyuan.scm.customer.entity.EnabledStatus;

public record WarehouseVO(
        Long id,
        String warehouseCode,
        String name,
        EnabledStatus status,
        Integer version,
        String address,
        String remark
) {
}
