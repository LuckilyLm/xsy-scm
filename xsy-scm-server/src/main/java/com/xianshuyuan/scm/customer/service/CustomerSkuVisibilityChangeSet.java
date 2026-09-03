package com.xianshuyuan.scm.customer.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.customer.dto.CustomerSkuVisibilityRequest;
import com.xianshuyuan.scm.customer.entity.CustomerSkuVisibilityEntity;

import java.util.*;

public record CustomerSkuVisibilityChangeSet(List<CustomerSkuVisibilityRequest> inserted,
                                             List<CustomerSkuVisibilityRequest> updated,
                                             List<Long> removedIds) {
    public CustomerSkuVisibilityChangeSet {
        inserted = List.copyOf(inserted);
        updated = List.copyOf(updated);
        removedIds = List.copyOf(removedIds);
    }

    public static CustomerSkuVisibilityChangeSet between(List<CustomerSkuVisibilityEntity> existing,
                                                          List<CustomerSkuVisibilityRequest> requested) {
        Map<Long, CustomerSkuVisibilityEntity> unmatched = new LinkedHashMap<>();
        existing.forEach(v -> unmatched.put(v.getId(), v));
        var inserted = new ArrayList<CustomerSkuVisibilityRequest>();
        var updated = new ArrayList<CustomerSkuVisibilityRequest>();
        var skuIds = new HashSet<Long>();
        for (var row : requested) {
            if (!skuIds.add(row.skuId())) {
                throw new BusinessException(CustomerErrorCodes.SKU_NOT_VISIBLE, "SKU 可见性重复");
            }
            if (row.id() == null) {
                inserted.add(row);
                continue;
            }
            CustomerSkuVisibilityEntity persisted = unmatched.remove(row.id());
            if (persisted == null) throw new BusinessException(CustomerErrorCodes.VISIBILITY_NOT_OWNED);
            if (!Objects.equals(persisted.getSkuId(), row.skuId())) {
                throw new BusinessException(CustomerErrorCodes.VISIBILITY_NOT_OWNED, "已存在的可见性记录不能更换 SKU");
            }
            updated.add(row);
        }
        return new CustomerSkuVisibilityChangeSet(inserted, updated, new ArrayList<>(unmatched.keySet()));
    }
}
