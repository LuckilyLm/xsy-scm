package com.xsy.scm.purchase.manager;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.purchase.constant.ScmPurchaseDemandStatusEnum;
import com.xsy.scm.purchase.domain.entity.PurchaseDemandEntity;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_DEMAND_ALLOCATION_CONFLICT;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_DEMAND_ALLOCATION_EXCEEDED;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_DEMAND_ITEM_NOT_OWNED;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_DEMAND_VERSION_CONFLICT;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_DEMAND_VERSION_REQUIRED;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_UNIT_CONVERSION_REQUIRED;

/**
 * 集中校验采购需求分配：数量、供应商、仓库和单位必须符合要求。 纯计算，不读取数据库；需求单位与采购单位不一致时直接拒绝，不自动换算。
 */
public final class PurchaseDemandAllocator {

    private PurchaseDemandAllocator() {
    }

    /**
     * 需求单位必须与采购单位一致，否则返回 40971。
     *
     * <p>
     * 比较是大小写敏感的字符串相等：单位是主数据里的展示值，不做归一化（归一化会把 {@code kg} 与 {@code KG} 视为可互换，掩盖真实的单位不一致）。
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
     * 需求状态必须允许分配（{@code PENDING} / {@code PARTIALLY_ALLOCATED} / {@code ALLOCATED} 三者都允许）。
     *
     * <p>
     * 这是对未知取值的防护，不是业务上的「不可分配」判定，分配本身可以重复发生（补分配）。
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
     * 采购需求的仓库在生成时确定，必须与采购单相同；需求首次分配后供应商也不能再改变。仓库或供应商不一致返回 40981。
     */
    public static void assignmentCompatible(Long orderSupplierId, Long orderWarehouseId, PurchaseDemandEntity demand) {
        if (demand.getWarehouseId() != null && !demand.getWarehouseId().equals(orderWarehouseId)) {
            throw new ScmBusinessException(PURCHASE_DEMAND_ALLOCATION_CONFLICT);
        }
        boolean firstAllocation = isFirstAllocation(demand);
        if (!firstAllocation && demand.getSupplierId() != null && !demand.getSupplierId().equals(orderSupplierId)) {
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
     * 首次分配且供应商尚未固定 → 调用方应把 {@code supplier_id} 记为采购单的供应商。
     */
    public static boolean shouldFixSupplier(PurchaseDemandEntity demand) {
        return isFirstAllocation(demand) && demand.getSupplierId() == null;
    }

    /**
     * 分配后的合计必须在 {@code [0, required]} 内，否则 40082。
     */
    public static void withinRequired(BigDecimal requiredQuantity, BigDecimal finalAllocatedQuantity) {
        if (finalAllocatedQuantity == null || finalAllocatedQuantity.signum() < 0
            || finalAllocatedQuantity.compareTo(requiredQuantity) > 0) {
            throw new ScmBusinessException(PURCHASE_DEMAND_ALLOCATION_EXCEEDED);
        }
    }

    /**
     * 需求状态由已分配数量与需求量确定：未分配为待处理，全部分配为已分配，中间为部分分配；不存一套独立的派生状态规则。
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
     * 需求必须按 demandId 升序逐个 {@code SELECT... FOR UPDATE}，避免与 {@code order.create} 路径交叉成环。返回去重后的升序列表。
     *
     * <p>
     * 调用方在锁定前校验需求 ID。这里过滤空值并去重，保证后续锁始终按升序获取。
     */
    public static List<Long> ascendingDemandIds(Collection<Long> demandIds) {
        return demandIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
    }
}
