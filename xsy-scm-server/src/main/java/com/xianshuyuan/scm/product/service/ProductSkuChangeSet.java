package com.xianshuyuan.scm.product.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.product.dto.ProductSkuSaveRequest;
import com.xianshuyuan.scm.product.entity.ProductSkuEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ProductSkuChangeSet(
        List<ProductSkuSaveRequest> inserted,
        List<ProductSkuSaveRequest> updated,
        List<Long> removedIds
) {

    public ProductSkuChangeSet {
        inserted = List.copyOf(inserted);
        updated = List.copyOf(updated);
        removedIds = List.copyOf(removedIds);
    }

    public static ProductSkuChangeSet between(
            List<ProductSkuEntity> existing,
            List<ProductSkuSaveRequest> requested
    ) {
        Map<Long, ProductSkuEntity> unmatched = new LinkedHashMap<>();
        existing.forEach(sku -> unmatched.put(sku.getId(), sku));
        List<ProductSkuSaveRequest> inserted = new ArrayList<>();
        List<ProductSkuSaveRequest> updated = new ArrayList<>();

        for (ProductSkuSaveRequest request : requested) {
            if (request.id() == null) {
                inserted.add(request);
                continue;
            }
            if (unmatched.remove(request.id()) == null) {
                throw new BusinessException(ProductErrorCodes.SKU_NOT_OWNED);
            }
            updated.add(request);
        }
        return new ProductSkuChangeSet(inserted, updated, new ArrayList<>(unmatched.keySet()));
    }
}
