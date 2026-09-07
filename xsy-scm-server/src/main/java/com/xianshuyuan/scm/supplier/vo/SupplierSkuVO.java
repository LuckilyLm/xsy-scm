package com.xianshuyuan.scm.supplier.vo;

import com.xianshuyuan.scm.customer.entity.EnabledStatus;

import java.util.Map;

public record SupplierSkuVO(
        Long id,
        Long supplierId,
        Long skuId,
        String supplierCodeSnapshot,
        String supplierNameSnapshot,
        String skuCodeSnapshot,
        String skuNameSnapshot,
        Map<String, String> specValuesSnapshot,
        String purchaseUnit,
        String referencePrice,
        Long purchaserId,
        Boolean defaultSupplier,
        EnabledStatus status,
        Integer version
) {
}
