package net.lab1024.sa.admin.module.scm.purchase.manager;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandAllocationEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_DEMAND_ALLOCATION_DUPLICATE;

/**
 * 采购单**分配级**差量（W5 Target Design §7.8 B 段，**Q13 修订的核心**）。
 *
 * <p><b>allocation 身份 = {@code (purchase_order_item_id, purchase_demand_id)}</b>，
 * 由 {@code uk_purchase_demand_allocation_source_active} 强制。
 *
 * <p><b>为什么必须有这个类</b>：A 源用 {@code Map<itemId, allocation>}（覆盖写）表达分配，
 * 导致「一行多需求」在编辑时只保留最后一条（A-D23）。W5 的算法**以 allocation 为主键集合**对账
 * （`Map<(itemId, demandId), allocation>`），于是
 * 「只改一个 allocation / 删一个 allocation / 保留其它 allocation」都是**独立的行级操作**，
 * **禁止**任何「一个 item 对一个 allocation」的算法。
 *
 * <p>规则：
 * <ul>
 *   <li>请求内同一 `(itemId, demandId)` 出现两次 → {@code PURCHASE_DEMAND_ALLOCATION_DUPLICATE}；</li>
 *   <li>两侧都有的 identity → 仅当**数量不同**才进 {@link #updated}（数量相同视为未变，
 *       不出现在任何列表中，避免无意义的写与版本膨胀）；更新项**继承旧行的
 *       `id` / `version` / `createdAt` / `createdBy`** —— `version` 是行级乐观锁的谓词，
 *       必须来自库中值（请求不带 allocation 自己的版本）；</li>
 *   <li>只在旧集合的 identity → {@link #removed}（**只删这一条**）；</li>
 *   <li>只在请求集合的 identity → {@link #inserted}。</li>
 * </ul>
 *
 * <p><b>调用方必须遍历 {@code 旧集合 ∪ 新集合} 的 demandId 去重算需求侧</b>：
 * 只在旧集合出现的 demand（被删空）也要重算，否则 {@code allocated_quantity} 不会回落、
 * {@code status} 也不会从 {@code ALLOCATED} 退回 {@code PENDING}（§7.8 C 段）。
 * 并集请用 {@link #involvedDemandIds}。
 */
public record PurchaseOrderAllocationChangeSet(List<PurchaseDemandAllocationEntity> inserted,
                                               List<PurchaseDemandAllocationEntity> updated,
                                               List<PurchaseDemandAllocationEntity> removed) {

    /**
     * allocation 的身份。
     */
    public record Key(Long purchaseOrderItemId, Long purchaseDemandId) {
    }

    public static Key key(PurchaseDemandAllocationEntity row) {
        return new Key(row.getPurchaseOrderItemId(), row.getPurchaseDemandId());
    }

    /**
     * 计算差量。`requested` 的 `purchaseOrderItemId` 由调用方在行落库后回填。
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
                continue;   // 数量未变：不写库、不动版本
            }
            row.setId(old.getId());
            // 行级乐观锁的版本必须**继承库中值**：请求只带 `demandVersion`（需求的版本），
            // 不带 allocation 自己的版本。漏掉这一步会让 `updateQuantity` 的
            // `WHERE version = ?` 拿到 null → 0 行受影响 → 并发写被误判为冲突。
            row.setVersion(old.getVersion());
            row.setCreatedAt(old.getCreatedAt());
            row.setCreatedBy(old.getCreatedBy());
            updated.add(row);
        }

        return new PurchaseOrderAllocationChangeSet(inserted, updated,
                new ArrayList<>(unmatched.values()));
    }

    /**
     * 本次涉及的全部 demandId（**旧集合 ∪ 新集合**），已去重且保持稳定顺序。
     */
    public List<Long> involvedDemandIds(List<PurchaseDemandAllocationEntity> existing) {
        var ids = new java.util.LinkedHashSet<Long>();
        existing.forEach(row -> ids.add(row.getPurchaseDemandId()));
        inserted.forEach(row -> ids.add(row.getPurchaseDemandId()));
        updated.forEach(row -> ids.add(row.getPurchaseDemandId()));
        return new ArrayList<>(ids);
    }
}
