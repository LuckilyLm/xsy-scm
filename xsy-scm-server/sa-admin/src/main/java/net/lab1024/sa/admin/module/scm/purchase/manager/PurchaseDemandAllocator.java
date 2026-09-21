package net.lab1024.sa.admin.module.scm.purchase.manager;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.purchase.constant.ScmPurchaseDemandStatusEnum;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandEntity;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_DEMAND_ALLOCATION_CONFLICT;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_DEMAND_ALLOCATION_EXCEEDED;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_DEMAND_ITEM_NOT_OWNED;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_DEMAND_VERSION_CONFLICT;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_DEMAND_VERSION_REQUIRED;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_UNIT_CONVERSION_REQUIRED;

/**
 * 采购需求分配规则（W5 Target Design §7.4 分配段 / §7.8 B–C 段 / §4.4）。
 *
 * <p>全部为**静态纯函数**，无 Spring、无 DB、无事务 —— 因此
 * 「超需求 / 跨 (supplier,warehouse) 冲突 / 按 demandId 升序 / 单位不一致拒绝」都能被单测直接覆盖。
 *
 * <p><b>Q17 是这个类存在的主要理由</b>：需求单位（销售单位）与采购单位
 * （{@code supplier_sku.purchase_unit}）是两个独立快照。两者不一致时**拒绝自动分配**（40971）；
 * **不允许**把「100 kg」仅替换单位字符串变成「100 箱」，也不允许猜换算系数。
 * 将来若需要换算，单独新增 Unit Conversion 能力（独立波次）。
 */
public final class PurchaseDemandAllocator {

    private PurchaseDemandAllocator() {
    }

    /**
     * Q17：需求单位必须与采购单位一致，否则 40971。
     *
     * <p>比较是**大小写敏感的字符串相等**：单位是主数据里冻结的展示值，
     * 不做归一化（归一化会把 `kg` 与 `KG` 视为可互换，从而掩盖真实的单位不一致）。
     */
    public static void unitCompatible(String demandUnit, String purchaseUnit) {
        if (demandUnit == null || purchaseUnit == null || !demandUnit.equals(purchaseUnit)) {
            throw new ScmBusinessException(PURCHASE_UNIT_CONVERSION_REQUIRED);
        }
    }

    /**
     * 采购行必须与需求同 SKU，否则 40995。
     */
    public static void itemMatchesDemand(Long itemSkuId, Long demandSkuId) {
        if (itemSkuId == null || !itemSkuId.equals(demandSkuId)) {
            throw new ScmBusinessException(PURCHASE_DEMAND_ITEM_NOT_OWNED);
        }
    }

    /**
     * 需求状态必须允许分配（§7.4：`PENDING` / `PARTIALLY_ALLOCATED` / `ALLOCATED` 三者都允许）。
     *
     * <p>这是对**未知取值**的白名单防护，不是业务上的「不可分配」判定 ——
     * 分配本身可以重复发生（补分配）。
     */
    public static void assignable(String demandStatus) {
        for (ScmPurchaseDemandStatusEnum candidate : ScmPurchaseDemandStatusEnum.values()) {
            if (candidate.name().equals(demandStatus)) {
                return;
            }
        }
        throw new ScmBusinessException(PURCHASE_DEMAND_ALLOCATION_CONFLICT);
    }

    /**
     * 需求版本必填（40091）且必须相等（40972）。
     */
    public static void demandVersion(Integer requestedVersion, Integer currentVersion) {
        if (requestedVersion == null) {
            throw new ScmBusinessException(PURCHASE_DEMAND_VERSION_REQUIRED);
        }
        if (!requestedVersion.equals(currentVersion)) {
            throw new ScmBusinessException(PURCHASE_DEMAND_VERSION_CONFLICT);
        }
    }

    /**
     * `(supplier, warehouse)` 一致性（40981）。
     *
     * <p>`warehouse_id` 在 `generate` 时已固定，因此必须始终等于采购单的仓库；
     * `supplier_id` 由**第一次分配**固定（`allocated_quantity == 0` 时由调用方写入），之后不得改变。
     * 所以：
     * <ul>
     *   <li>需求已有仓库且与采购单仓库不等 → 40981；</li>
     *   <li>需求已分配过（`allocated > 0`）且供应商与采购单不等 → 40981。</li>
     * </ul>
     */
    public static void assignmentCompatible(Long orderSupplierId, Long orderWarehouseId, PurchaseDemandEntity demand) {
        if (demand.getWarehouseId() != null && !demand.getWarehouseId().equals(orderWarehouseId)) {
            throw new ScmBusinessException(PURCHASE_DEMAND_ALLOCATION_CONFLICT);
        }
        boolean firstAllocation = isFirstAllocation(demand);
        if (!firstAllocation && demand.getSupplierId() != null
                && !demand.getSupplierId().equals(orderSupplierId)) {
            throw new ScmBusinessException(PURCHASE_DEMAND_ALLOCATION_CONFLICT);
        }
    }

    /**
     * 是否处于「尚未发生任何分配」的状态。
     */
    public static boolean isFirstAllocation(PurchaseDemandEntity demand) {
        BigDecimal allocated = demand.getAllocatedQuantity();
        return allocated == null || allocated.signum() == 0;
    }

    /**
     * 首次分配且供应商尚未固定 → 调用方应把 `supplier_id` 落为采购单的供应商。
     */
    public static boolean shouldFixSupplier(PurchaseDemandEntity demand) {
        return isFirstAllocation(demand) && demand.getSupplierId() == null;
    }

    /**
     * 分配后的合计必须在 `[0, required]` 内，否则 40082。
     */
    public static void withinRequired(BigDecimal requiredQuantity, BigDecimal finalAllocatedQuantity) {
        if (finalAllocatedQuantity == null
                || finalAllocatedQuantity.signum() < 0
                || finalAllocatedQuantity.compareTo(requiredQuantity) > 0) {
            throw new ScmBusinessException(PURCHASE_DEMAND_ALLOCATION_EXCEEDED);
        }
    }

    /**
     * §4.4：由 `allocated` 与 `required` 推导需求状态。
     *
     * <pre>
     * allocated == 0        → PENDING
     * allocated == required → ALLOCATED
     * 其它                  → PARTIALLY_ALLOCATED
     * </pre>
     *
     * <p>**必须能回落**：编辑采购单删掉某个 demand 的全部分配后，`allocated` 归零、
     * 状态必须从 `ALLOCATED` 退回 `PENDING`（§7.8 C 段的并集遍历就是为此）。
     */
    public static String statusFor(BigDecimal requiredQuantity, BigDecimal allocatedQuantity) {
        if (allocatedQuantity == null || allocatedQuantity.signum() == 0) {
            return ScmPurchaseDemandStatusEnum.PENDING.name();
        }
        if (allocatedQuantity.compareTo(requiredQuantity) == 0) {
            return ScmPurchaseDemandStatusEnum.ALLOCATED.name();
        }
        return ScmPurchaseDemandStatusEnum.PARTIALLY_ALLOCATED.name();
    }

    /**
     * P12 锁序：需求必须**按 demandId 升序**逐个 `SELECT ... FOR UPDATE`，
     * 避免与 `order.create` 路径交叉成环。返回去重后的升序列表。
     *
     * <p>{@code null} 元素被过滤而不是抛 {@code NullPointerException}：`demandId` 为空
     * 已在 {@code PurchaseOrderValidator.draft} 以 40090 拦下，走到锁序阶段时它只可能是
     * 上游漏检的脏数据 —— 此时抛 NPE 会掩盖真实的校验缺口，静默丢弃又不可接受，
     * 因此这里只做**归一化**，由调用方的 `demandId != null` 断言负责报错。
     */
    public static List<Long> ascendingDemandIds(Collection<Long> demandIds) {
        return demandIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
    }
}
