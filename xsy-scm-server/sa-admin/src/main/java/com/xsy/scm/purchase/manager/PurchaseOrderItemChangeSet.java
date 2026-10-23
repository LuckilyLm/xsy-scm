package com.xsy.scm.purchase.manager;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.purchase.domain.entity.PurchaseOrderItemEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ITEM_VERSION_REQUIRED;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_ITEM_DUPLICATE_SKU;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_ITEM_NOT_OWNED;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_ITEM_VERSION_CONFLICT;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_STATE_INVALID;

/**
 * 采购单明细用采购单 ID 与商品规格 ID 唯一确定，需求分配用采购明细 ID 与需求 ID 确定。提交时校验重复规格、行归属和版本，统一计算新增、修改与删除。
 */
public record PurchaseOrderItemChangeSet(List<PurchaseOrderItemEntity> inserted, List<PurchaseOrderItemEntity> updated,
    List<PurchaseOrderItemEntity> removed) {

    /**
     * 计算差量。{@code requested} 里的 {@code id} / {@code version} 由表单带入，其余字段已由 {@code PurchaseSnapshotFactory} 装配完成。
     */
    public static PurchaseOrderItemChangeSet between(List<PurchaseOrderItemEntity> existing,
        List<PurchaseOrderItemEntity> requested) {
        var unmatched = new LinkedHashMap<Long, PurchaseOrderItemEntity>();
        existing.forEach(row -> unmatched.put(row.getId(), row));

        var skus = new java.util.HashSet<Long>();
        var inserted = new ArrayList<PurchaseOrderItemEntity>();
        var updated = new ArrayList<PurchaseOrderItemEntity>();

        for (PurchaseOrderItemEntity row : requested) {
            if (row.getSkuId() == null || !skus.add(row.getSkuId())) {
                throw new ScmBusinessException(PURCHASE_ORDER_ITEM_DUPLICATE_SKU);
            }
            if (row.getId() == null) {
                inserted.add(row);
                continue;
            }
            PurchaseOrderItemEntity old = unmatched.remove(row.getId());
            if (old == null) {
                throw new ScmBusinessException(PURCHASE_ORDER_ITEM_NOT_OWNED);
            }
            if (!Objects.equals(old.getSkuId(), row.getSkuId())) {
                // 保留行的 id 与 skuId 必须自洽：否则等于把该行悄悄换成了另一个商品
                throw new ScmBusinessException(PURCHASE_ORDER_ITEM_NOT_OWNED);
            }
            if (row.getVersion() == null) {
                throw new ScmBusinessException(PURCHASE_ITEM_VERSION_REQUIRED);
            }
            if (!Objects.equals(row.getVersion(), old.getVersion())) {
                throw new ScmBusinessException(PURCHASE_ORDER_ITEM_VERSION_CONFLICT);
            }
            // 保留行沿用原有的创建审计信息（编辑不是新建）
            row.setCreatedAt(old.getCreatedAt());
            row.setCreatedBy(old.getCreatedBy());
            updated.add(row);
        }

        var removed = new ArrayList<PurchaseOrderItemEntity>(unmatched.values());
        for (PurchaseOrderItemEntity row : removed) {
            BigDecimal received = row.getReceivedQuantity();
            if (received != null && received.signum() > 0) {
                throw new ScmBusinessException(PURCHASE_ORDER_STATE_INVALID);
            }
        }
        return new PurchaseOrderItemChangeSet(inserted, updated, removed);
    }
}
