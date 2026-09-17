package net.lab1024.sa.admin.module.scm.purchase.manager;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseOrderItemEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ITEM_VERSION_REQUIRED;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_ITEM_DUPLICATE_SKU;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_ITEM_NOT_OWNED;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_ITEM_VERSION_CONFLICT;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_STATE_INVALID;

/**
 * 采购单**行级**差量（W5 Target Design §7.8 A 段）。
 *
 * <p><b>行身份 = {@code (purchase_order_id, sku_id)}</b>，由
 * {@code uk_purchase_order_item_order_sku_active} 强制（Q13 修订后**保留**该索引）。
 * 这与 allocation 身份 `(purchase_order_item_id, purchase_demand_id)` 是两个层次，
 * 必须区分（见 {@link PurchaseOrderAllocationChangeSet}）。
 *
 * <p>规则（逐条对应 §7.8 步骤 1–4）：
 * <ol>
 *   <li>请求内同一 `skuId` 出现两次 → {@code PURCHASE_ORDER_ITEM_DUPLICATE_SKU}；</li>
 *   <li>保留行（`id` 非空）必须属于本单 → 否则 {@code PURCHASE_ORDER_ITEM_NOT_OWNED}；</li>
 *   <li>保留行必须带 `version` → 否则 {@code PURCHASE_ITEM_VERSION_REQUIRED}；
 *       版本必须相等 → 否则 {@code PURCHASE_ORDER_ITEM_VERSION_CONFLICT}；</li>
 *   <li>被删除的行若已收货（`received_quantity > 0`）→ {@code PURCHASE_ORDER_STATE_INVALID}。</li>
 * </ol>
 */
public record PurchaseOrderItemChangeSet(List<PurchaseOrderItemEntity> inserted,
                                         List<PurchaseOrderItemEntity> updated,
                                         List<PurchaseOrderItemEntity> removed) {

    /**
     * 计算差量。`requested` 里的 `id` / `version` 由表单带入，其余字段已由
     * {@code PurchaseSnapshotFactory} 装配完成。
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
