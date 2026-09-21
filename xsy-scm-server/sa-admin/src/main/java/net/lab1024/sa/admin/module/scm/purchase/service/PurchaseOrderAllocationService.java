package net.lab1024.sa.admin.module.scm.purchase.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.product.dao.ProductSkuOptionDao;
import net.lab1024.sa.admin.module.scm.product.dao.ProductSpuDao;
import net.lab1024.sa.admin.module.scm.product.domain.entity.ProductSpuEntity;
import net.lab1024.sa.admin.module.scm.product.domain.vo.ProductSkuOptionVO;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseDemandAllocationDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseDemandDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseOrderItemDao;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandAllocationEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseOrderEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseOrderItemEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderAddForm;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseDemandAllocator;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseEntityStamper;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseOrderAllocationChangeSet;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseOrderValidator;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseSnapshotFactory;
import net.lab1024.sa.admin.module.scm.supplier.domain.entity.SupplierSkuEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_DEMAND_NOT_FOUND;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_SUPPLIER_SKU_DISABLED;

/**
 * 采购行装配与需求分配对账，由 {@link PurchaseOrderService} 的事务方法调用。
 * 新建先按需求 ID 升序加锁，再写采购单；编辑先锁单据和采购行，再读取旧分配并锁旧、新需求并集。
 * 取消和删除持有单据锁后释放分配，并重算需求数量与状态；少收关单不释放分配。
 */
@Service
@RequiredArgsConstructor
public class PurchaseOrderAllocationService {

    private final ProductSkuOptionDao skus;

    private final ProductSpuDao spus;

    private final PurchaseDemandDao purchaseDemandDao;

    private final PurchaseDemandAllocationDao purchaseDemandAllocationDao;

    private final PurchaseOrderItemDao purchaseOrderItemDao;

    private final PurchaseOrderValidator purchaseOrderValidator;

    /**
     * 请求行 + 请求分配（Q13：一行 N 条分配）。
     *
     * <p>`allocations` 在校验阶段被填充为真正的 {@link PurchaseDemandAllocationEntity}
     * —— 因为快照字段（`salesOrderId` / `salesOrderItemId` / `skuId` / `demandSnapshot`）
     * 只能从**已锁定的需求行**取，不能在装配商品时凭空造。
     */
    public static final class RequestedRow {

        final PurchaseOrderItemEntity item;

        final List<PurchaseOrderAddForm.Allocation> forms;

        final List<PurchaseDemandAllocationEntity> allocations = new ArrayList<>();

        private RequestedRow(PurchaseOrderItemEntity item, List<PurchaseOrderAddForm.Allocation> forms) {
            this.item = item;
            this.forms = forms == null ? List.of() : forms;
        }
    }

    public List<RequestedRow> materialize(PurchaseOrderAddForm form) {
        List<Long> skuIds = form.getItems().stream()
                .map(PurchaseOrderAddForm.Item::getSkuId)
                .distinct()
                .toList();
        Map<Long, ProductSkuOptionVO> products = skus.selectByIds(skuIds).stream()
                .collect(Collectors.toMap(ProductSkuOptionVO::getSkuId, Function.identity(), (a, b) -> a));
        List<Long> spuIds = products.values().stream()
                .map(ProductSkuOptionVO::getSpuId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> spuCodes = spuIds.isEmpty()
                ? Map.of()
                : spus.selectBatchIds(spuIds).stream().collect(Collectors.toMap(
                ProductSpuEntity::getId, ProductSpuEntity::getSpuCode, (a, b) -> a));

        List<RequestedRow> rows = new ArrayList<>(form.getItems().size());
        for (PurchaseOrderAddForm.Item itemForm : form.getItems()) {
            ProductSkuOptionVO sku = products.get(itemForm.getSkuId());
            if (sku == null) {
                // 商品不存在 / 不可售：与 W2 的采购判定入口共用同一个对外码（40992），
                // 不让 W1 的内部码从采购 API 泄漏出去
                throw new ScmBusinessException(PURCHASE_SUPPLIER_SKU_DISABLED);
            }
            SupplierSkuEntity supplierSku =
                    purchaseOrderValidator.requirePurchasableSku(form.getSupplierId(), itemForm.getSkuId());
            PurchaseOrderItemEntity item = PurchaseSnapshotFactory.item(
                    itemForm, sku, spuCodes.get(sku.getSpuId()), supplierSku);
            rows.add(new RequestedRow(item, itemForm.getAllocations()));
        }
        return rows;
    }

    /**
     * 校验分配集合并补全快照字段；返回「本次请求按 demandId 的合计」。
     *
     * <p>逐条对应 §7.8 B 段第 7 步与 §7.4 的分配校验表。
     *
     * <p><b>累计上限必须扣除本单旧值</b>：`demand.allocated_quantity` 是**全库**已分配合计，
     * 其中已含本单上一次提交的量。`create` 时本单尚无分配（传空 Map），`update` 时必须传入
     * 旧合计，否则「原样保存」都会因为 `旧 + 新 > required` 而误报 40082。
     * 这与 {@code recomputeDemands} 里 `otherAllocated = allocated − oldTotals[demand]` 是同一个口径。
     */
    public Map<Long, BigDecimal> validateAllocations(List<RequestedRow> rows,
                                                     Map<Long, PurchaseDemandEntity> demands,
                                                     Long supplierId,
                                                     Long warehouseId,
                                                     Map<Long, BigDecimal> oldTotals) {
        Map<Long, BigDecimal> totals = new LinkedHashMap<>();
        for (RequestedRow row : rows) {
            for (PurchaseOrderAddForm.Allocation allocationForm : row.forms) {
                PurchaseDemandEntity demand = demands.get(allocationForm.getDemandId());
                if (demand == null) {
                    throw new ScmBusinessException(PURCHASE_DEMAND_NOT_FOUND);
                }
                // 需求版本必填（40091）+ 必须等于当前版本（40972）
                PurchaseDemandAllocator.demandVersion(allocationForm.getDemandVersion(), demand.getVersion());
                // 同一 SKU 才能挂（40995）
                PurchaseDemandAllocator.itemMatchesDemand(row.item.getSkuId(), demand.getSkuId());
                // Q17：需求单位必须等于采购单位，W5 不换算（40971）
                PurchaseDemandAllocator.unitCompatible(
                        demand.getDemandUnitSnapshot(), row.item.getPurchaseUnitSnapshot());
                // (supplier, warehouse) 一致性；首次分配时由调用方固定 supplier（40981）
                PurchaseDemandAllocator.assignmentCompatible(supplierId, warehouseId, demand);

                BigDecimal quantity = PurchaseOrderValidator.decimal(allocationForm.getQuantity(), true);
                totals.merge(demand.getId(), quantity, BigDecimal::add);

                PurchaseDemandAllocationEntity allocation = new PurchaseDemandAllocationEntity();
                // 保留行此刻已有真实 itemId（身份的一半）；新增行落库后再回填
                allocation.setPurchaseOrderItemId(row.item.getId());
                allocation.setPurchaseDemandId(demand.getId());
                allocation.setSalesOrderId(demand.getSalesOrderId());
                allocation.setSalesOrderItemId(demand.getSalesOrderItemId());
                allocation.setSkuId(demand.getSkuId());
                allocation.setAllocatedQuantity(quantity);
                allocation.setDemandSnapshot(PurchaseSnapshotFactory.allocationDemandSnapshot(demand));
                row.allocations.add(allocation);
            }
        }

        for (Map.Entry<Long, BigDecimal> entry : totals.entrySet()) {
            PurchaseDemandEntity demand = demands.get(entry.getKey());
            // 扣除本单旧分配后再比 required（见方法注释）。create 时 oldTotals 为空 → 退化为原口径。
            BigDecimal otherAllocated = demand.getAllocatedQuantity()
                    .subtract(oldTotals.getOrDefault(entry.getKey(), BigDecimal.ZERO));
            PurchaseDemandAllocator.withinRequired(demand.getRequiredQuantity(),
                    otherAllocated.add(entry.getValue()));
        }
        return totals;
    }

    /**
     * §7.8 C 段：按 `旧 ∪ 新` 的 demandId 升序逐个重算需求侧。
     *
     * <p>**必须遍历并集**：只在旧集合出现的 demand（被删空 / 整单取消）也要重算，
     * 否则 `allocated_quantity` 不会回落、`status` 也不会从 `ALLOCATED` 退回 `PENDING`。
     */
    public void recomputeDemands(Map<Long, PurchaseDemandEntity> locked,
                                 Map<Long, BigDecimal> oldTotals,
                                 Map<Long, BigDecimal> newTotals,
                                 Long orderSupplierId) {
        Collection<Long> involved = new LinkedHashSet<>(oldTotals.keySet());
        involved.addAll(newTotals.keySet());
        for (Long demandId : PurchaseDemandAllocator.ascendingDemandIds(involved)) {
            PurchaseDemandEntity demand = locked.get(demandId);
            if (demand == null) {
                throw new ScmBusinessException(PURCHASE_DEMAND_NOT_FOUND);
            }
            BigDecimal otherAllocated = demand.getAllocatedQuantity()
                    .subtract(oldTotals.getOrDefault(demandId, BigDecimal.ZERO));
            BigDecimal finalAllocated = otherAllocated
                    .add(newTotals.getOrDefault(demandId, BigDecimal.ZERO));
            PurchaseDemandAllocator.withinRequired(demand.getRequiredQuantity(), finalAllocated);

            Long supplierId = demand.getSupplierId();
            if (PurchaseDemandAllocator.shouldFixSupplier(demand) && finalAllocated.signum() > 0) {
                // supplier 由「第一次分配」固定，之后不得改变（§7.4）
                supplierId = orderSupplierId;
            }
            String status = PurchaseDemandAllocator.statusFor(
                    demand.getRequiredQuantity(), finalAllocated);
            if (purchaseDemandDao.updateAllocation(demand.getId(), demand.getVersion(),
                    finalAllocated, status, supplierId, ScmOperator.current()) != 1) {
                throw new ScmBusinessException(VERSION_CONFLICT);
            }
        }
    }

    /**
     * 单行内的分配集合差量（Q13：**禁止**「一个 item 对一个 allocation」的算法）。
     */
    public void applyAllocationChanges(RequestedRow row,
                                       List<PurchaseDemandAllocationEntity> existing) {
        // 新增采购行在落库后才取得 ID；差量身份和插入记录都必须使用该 ID。
        row.allocations.forEach(allocation -> allocation.setPurchaseOrderItemId(row.item.getId()));
        PurchaseOrderAllocationChangeSet changes =
                PurchaseOrderAllocationChangeSet.between(existing, row.allocations);
        for (PurchaseDemandAllocationEntity removed : changes.removed()) {
            // 只删这一条：同一行的其它 allocation 必须原样保留
            if (purchaseDemandAllocationDao.softDelete(
                    removed.getId(), removed.getVersion(), ScmOperator.current()) != 1) {
                throw new ScmBusinessException(VERSION_CONFLICT);
            }
        }
        for (PurchaseDemandAllocationEntity updated : changes.updated()) {
            // 不重建行：同 (item, demand) 的其它分配不受影响
            if (purchaseDemandAllocationDao.updateQuantity(updated.getId(), updated.getVersion(),
                    updated.getAllocatedQuantity(), ScmOperator.current()) != 1) {
                throw new ScmBusinessException(VERSION_CONFLICT);
            }
        }
        for (PurchaseDemandAllocationEntity inserted : changes.inserted()) {
            PurchaseEntityStamper.stamp(inserted, true);
            purchaseDemandAllocationDao.insert(inserted);
        }
    }

    /**
     * 新建行的全部分配（没有旧集合可对账）。
     */
    public void insertAllocations(RequestedRow row) {
        for (PurchaseDemandAllocationEntity allocation : row.allocations) {
            allocation.setPurchaseOrderItemId(row.item.getId());
            PurchaseEntityStamper.stamp(allocation, true);
            purchaseDemandAllocationDao.insert(allocation);
        }
    }

    /**
     * 释放本单的全部分配并重算需求（`cancel` / `delete`）。
     *
     * <p>见类注释：不释放会让需求永久卡在 {@code ALLOCATED}。**不删任何行、不删任何单据**
     * —— 只把分配软删、把需求的 `allocated_quantity` 减回去。
     */
    public void releaseAllocations(PurchaseOrderEntity order) {
        List<PurchaseOrderItemEntity> items = purchaseOrderItemDao.listByOrderId(order.getId());
        if (items.isEmpty()) {
            return;
        }
        Map<Long, List<PurchaseDemandAllocationEntity>> byItem = loadAllocations(items);
        List<PurchaseDemandAllocationEntity> allocations = byItem.values().stream()
                .flatMap(List::stream)
                .toList();
        if (allocations.isEmpty()) {
            return;
        }
        Map<Long, BigDecimal> oldTotals = totals(byItem.values());
        Map<Long, PurchaseDemandEntity> demands = lockDemands(oldTotals.keySet());
        for (PurchaseOrderItemEntity item : items) {
            purchaseDemandAllocationDao.softDeleteByOrderItemId(item.getId(), ScmOperator.current());
        }
        recomputeDemands(demands, oldTotals, Map.of(), order.getSupplierId());
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    public Map<Long, List<PurchaseDemandAllocationEntity>> loadAllocations(
            List<PurchaseOrderItemEntity> items) {
        if (items.isEmpty()) {
            return Map.of();
        }
        List<Long> itemIds = items.stream().map(PurchaseOrderItemEntity::getId).toList();
        return purchaseDemandAllocationDao.listActiveByOrderItemIds(itemIds).stream()
                .collect(Collectors.groupingBy(PurchaseDemandAllocationEntity::getPurchaseOrderItemId));
    }

    public static List<Long> requestedDemandIds(List<RequestedRow> rows) {
        return rows.stream()
                .flatMap(row -> row.forms.stream())
                .map(PurchaseOrderAddForm.Allocation::getDemandId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * P12 锁序第 1 层：需求必须**按 id 升序**一次性锁完（{@code ORDER BY id ASC FOR UPDATE}）。
     */
    public Map<Long, PurchaseDemandEntity> lockDemands(Collection<Long> demandIds) {
        List<Long> ascending = PurchaseDemandAllocator.ascendingDemandIds(demandIds);
        if (ascending.isEmpty()) {
            return Map.of();
        }
        List<PurchaseDemandEntity> rows = purchaseDemandDao.lockByIds(ascending);
        if (rows.size() != ascending.size()) {
            throw new ScmBusinessException(PURCHASE_DEMAND_NOT_FOUND);
        }
        Map<Long, PurchaseDemandEntity> byId = new LinkedHashMap<>();
        rows.forEach(row -> byId.put(row.getId(), row));
        return byId;
    }

    public static Map<Long, BigDecimal> totals(Collection<List<PurchaseDemandAllocationEntity>> grouped) {
        Map<Long, BigDecimal> totals = new LinkedHashMap<>();
        grouped.stream().flatMap(List::stream).forEach(allocation ->
                totals.merge(allocation.getPurchaseDemandId(),
                        allocation.getAllocatedQuantity(), BigDecimal::add));
        return totals;
    }
}
