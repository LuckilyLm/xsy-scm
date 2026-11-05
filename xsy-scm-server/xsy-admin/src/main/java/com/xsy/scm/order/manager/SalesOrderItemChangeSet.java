package com.xsy.scm.order.manager;

import com.xsy.scm.order.domain.entity.SalesOrderItemEntity;

import com.xsy.scm.common.exception.ScmBusinessException;

import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_ITEM_NOT_OWNED;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_ITEM_VERSION_CONFLICT;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_ITEM_VERSION_REQUIRED;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

public record SalesOrderItemChangeSet(List<SalesOrderItemEntity> inserted, List<SalesOrderItemEntity> updated,
    List<SalesOrderItemEntity> removed) {
    public static SalesOrderItemChangeSet between(List<SalesOrderItemEntity> existing,
        List<SalesOrderItemEntity> requested) {
        var unmatched = new LinkedHashMap<Long, SalesOrderItemEntity>();
        existing.forEach(existingItem -> unmatched.put(existingItem.getId(), existingItem));
        var inserted = new ArrayList<SalesOrderItemEntity>();
        var updated = new ArrayList<SalesOrderItemEntity>();
        var removed = new ArrayList<SalesOrderItemEntity>();
        for (var requestedItem : requested) {
            if (requestedItem.getId() == null) {
                inserted.add(requestedItem);
                continue;
            }
            var existingItem = unmatched.remove(requestedItem.getId());
            if (existingItem == null)
                throw new ScmBusinessException(ORDER_ITEM_NOT_OWNED);
            if (requestedItem.getVersion() == null)
                throw new ScmBusinessException(ORDER_ITEM_VERSION_REQUIRED);
            if (!Objects.equals(requestedItem.getVersion(), existingItem.getVersion()))
                throw new ScmBusinessException(ORDER_ITEM_VERSION_CONFLICT);
            // 规格变化就是另一条业务明细，不能继承旧报价、实重或已关联的分拣/出库记录。
            if (!Objects.equals(requestedItem.getSkuId(), existingItem.getSkuId())) {
                removed.add(existingItem);
                requestedItem.setId(null);
                requestedItem.setVersion(0);
                inserted.add(requestedItem);
                continue;
            }
            requestedItem.setCreatedAt(existingItem.getCreatedAt());
            requestedItem.setCreatedBy(existingItem.getCreatedBy());
            updated.add(requestedItem);
        }
        removed.addAll(unmatched.values());
        return new SalesOrderItemChangeSet(inserted, updated, removed);
    }
}
