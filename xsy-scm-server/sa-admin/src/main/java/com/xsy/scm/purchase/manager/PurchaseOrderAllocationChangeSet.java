package com.xsy.scm.purchase.manager;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.purchase.domain.entity.PurchaseDemandAllocationEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_DEMAND_ALLOCATION_DUPLICATE;

/** 按采购明细与需求的组合识别分配记录。先校验本次变更，再统一写入，避免分配数量重复。 */
public record PurchaseOrderAllocationChangeSet(List<PurchaseDemandAllocationEntity> inserted,
        List<PurchaseDemandAllocationEntity> updated, List<PurchaseDemandAllocationEntity> removed) {

    /**
     * allocation 的身份。
     */
    public record Key(Long purchaseOrderItemId, Long purchaseDemandId) {
    }

    public static Key key(PurchaseDemandAllocationEntity row) {
        return new Key(row.getPurchaseOrderItemId(), row.getPurchaseDemandId());
    }

    /**
     * 计算差量。{@code requested} 的 {@code purchaseOrderItemId} 由调用方在行落库后回填。
     */
    public static PurchaseOrderAllocationChangeSet between(List<PurchaseDemandAllocationEntity> existing,
            List<PurchaseDemandAllocationEntity> requested) {
        var unmatched = new LinkedHashMap<Key, PurchaseDemandAllocationEntity>();
        existing.forEach(row -> unmatched.put(key(row), row));

        var inserted = new ArrayList<PurchaseDemandAllocationEntity>();
        var updated = new ArrayList<PurchaseDemandAllocationEntity>();
        var seen = new java.util.HashSet<Key>();

        for (PurchaseDemandAllocationEntity row : requested) {
            Key key = key(row);
            if (!seen.add(key)) {
                throw new ScmBusinessException(PURCHASE_DEMAND_ALLOCATION_DUPLICATE);
            }
            PurchaseDemandAllocationEntity old = unmatched.remove(key);
            if (old == null) {
                inserted.add(row);
                continue;
            }
            if (Objects.equals(old.getAllocatedQuantity(), row.getAllocatedQuantity())) {
                continue; // 数量未变：不写库、不动版本
            }
            row.setId(old.getId());
            // 行级乐观锁的版本必须<b>继承库中值</b>：请求只带 {@code demandVersion}（需求的版本），
            // 不带 allocation 自己的版本。漏掉这一步会让 {@code updateQuantity} 的
            // {@code WHERE version = ?} 拿到 null → 0 行受影响 → 并发写被误判为冲突。
            row.setVersion(old.getVersion());
            row.setCreatedAt(old.getCreatedAt());
            row.setCreatedBy(old.getCreatedBy());
            updated.add(row);
        }

        return new PurchaseOrderAllocationChangeSet(inserted, updated, new ArrayList<>(unmatched.values()));
    }

    /**
     * 本次涉及的全部 demandId（<b>旧集合 ∪ 新集合</b>），已去重且保持稳定顺序。
     */
    public List<Long> involvedDemandIds(List<PurchaseDemandAllocationEntity> existing) {
        var ids = new java.util.LinkedHashSet<Long>();
        existing.forEach(row -> ids.add(row.getPurchaseDemandId()));
        inserted.forEach(row -> ids.add(row.getPurchaseDemandId()));
        updated.forEach(row -> ids.add(row.getPurchaseDemandId()));
        return new ArrayList<>(ids);
    }
}
