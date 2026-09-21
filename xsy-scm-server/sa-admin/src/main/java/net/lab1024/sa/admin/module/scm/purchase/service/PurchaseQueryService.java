package net.lab1024.sa.admin.module.scm.purchase.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseDemandAllocationDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseDemandDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseOperationLogDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseOrderDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseOrderItemDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseReceiptDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseReceiptItemDao;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandAllocationEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseOrderItemEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseReceiptItemEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseDemandQueryForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderQueryForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptQueryForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseDemandVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOperationLogVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderAllocationVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderItemVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptItemVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptVO;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseReceiptQuantityCalculator;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_DEMAND_NOT_FOUND;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_NOT_FOUND;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_RECEIPT_NOT_FOUND;

/**
 * 采购域只读查询（W5 Target Design §7.1 的 7 个只读端点）。
 *
 * <p>**只读**：本类不写任何表、不开写事务，也不抛业务冲突码（只抛 4048x 的「不存在」）。
 * 三个命令服务都通过本类取详情 VO 作为返回值 —— 这样「列表、详情、命令返回值」三处
 * 永远是同一套投影，不会出现「命令返回的 VO 少一个字段」的漂移（W4 的
 * {@code SalesOrderQueryService} 就是这个角色）。
 *
 * <p>**日志只有采购单一个端点**（{@code GET /scm/purchase/log/{orderId}}）：
 * §7.1 的端点清单是 3（需求）+ 11（采购单）+ 8（收货）+ 5（仓库）= **27**，
 * 与 §11.3 / DoD 第 8 条一致 —— 因此本类**不提供** `receiptLogs`（没有端点消费它）。
 * 收货单的日志通过 `purchase_receipt_id` 直接查 `purchase_operation_log`
 * （见 {@code idx_purchase_operation_log_receipt_created}），不需要额外的 HTTP 入口。
 *
 * <p>**派生字段一律在这里算，不在 SQL 里算**：`remaining` / `overReceipt` 走
 * {@link PurchaseReceiptQuantityCalculator} 的同一个纯函数，与 DB 的
 * {@code ck_purchase_receipt_item_reconciliation} 用同一套公式；
 * 若 SQL 与 Java 各写一遍，两边会在边界上悄悄分叉。
 */
@Service
@RequiredArgsConstructor
public class PurchaseQueryService {

    private final PurchaseDemandDao purchaseDemandDao;

    private final PurchaseDemandAllocationDao purchaseDemandAllocationDao;

    private final PurchaseOrderDao purchaseOrderDao;

    private final PurchaseOrderItemDao purchaseOrderItemDao;

    private final PurchaseReceiptDao purchaseReceiptDao;

    private final PurchaseReceiptItemDao purchaseReceiptItemDao;

    private final PurchaseOperationLogDao purchaseOperationLogDao;

    // ------------------------------------------------------------------
    // 采购需求
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResult<PurchaseDemandVO> demandQuery(PurchaseDemandQueryForm form) {
        var page = SmartPageUtil.convert2PageQuery(form);
        return SmartPageUtil.convert2PageResult(page, purchaseDemandDao.query(page, form));
    }

    @Transactional(readOnly = true)
    public PurchaseDemandVO demandDetail(Long id) {
        PurchaseDemandVO vo = purchaseDemandDao.detail(id);
        if (vo == null) {
            throw new net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException(
                    PURCHASE_DEMAND_NOT_FOUND);
        }
        return vo;
    }

    // ------------------------------------------------------------------
    // 采购单
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResult<PurchaseOrderVO> orderQuery(PurchaseOrderQueryForm form) {
        var page = SmartPageUtil.convert2PageQuery(form);
        return SmartPageUtil.convert2PageResult(page, purchaseOrderDao.query(page, form));
    }

    /**
     * 详情 = 单头 + 全部行（含每行分配）+ 单级分配平铺 + 全量日志。
     */
    @Transactional(readOnly = true)
    public PurchaseOrderVO orderDetail(Long id) {
        PurchaseOrderVO vo = purchaseOrderDao.detail(id);
        if (vo == null) {
            throw new net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException(
                    PURCHASE_ORDER_NOT_FOUND);
        }
        List<PurchaseOrderItemVO> items = orderItems(id);
        vo.setItems(items);
        // 单级平铺：前端「分配明细」区直接消费，不需要自己扁平化 items[].allocations[]
        vo.setAllocations(items.stream()
                .flatMap(item -> item.getAllocations().stream())
                .toList());
        vo.setLogs(orderLogs(id));
        return vo;
    }

    /**
     * 采购单行（含每行的分配集合，**Q13：N allocations**）。
     */
    @Transactional(readOnly = true)
    public List<PurchaseOrderItemVO> orderItems(Long orderId) {
        List<PurchaseOrderItemEntity> rows = purchaseOrderItemDao.listByOrderId(orderId);
        return itemVos(rows);
    }

    @Transactional(readOnly = true)
    public List<PurchaseOperationLogVO> orderLogs(Long orderId) {
        return purchaseOperationLogDao.listByOrderId(orderId);
    }

    // ------------------------------------------------------------------
    // 收货单
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResult<PurchaseReceiptVO> receiptQuery(PurchaseReceiptQueryForm form) {
        var page = SmartPageUtil.convert2PageQuery(form);
        return SmartPageUtil.convert2PageResult(page, purchaseReceiptDao.query(page, form));
    }

    @Transactional(readOnly = true)
    public PurchaseReceiptVO receiptDetail(Long id) {
        PurchaseReceiptVO vo = purchaseReceiptDao.detail(id);
        if (vo == null) {
            throw new net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException(
                    PURCHASE_RECEIPT_NOT_FOUND);
        }
        vo.setItems(receiptItems(id));
        return vo;
    }

    @Transactional(readOnly = true)
    public List<PurchaseReceiptItemVO> receiptItems(Long receiptId) {
        return purchaseReceiptItemDao.listByReceiptId(receiptId).stream()
                .map(PurchaseQueryService::receiptItemVo)
                .toList();
    }

    // ------------------------------------------------------------------
    // 实体 → VO（显式映射：列名快照与 VO 字段名**故意不同**，不用 BeanUtils 猜）
    // ------------------------------------------------------------------

    /**
     * 批量装配采购单行 VO。
     *
     * <p>一次取全部行的 allocation，再按 `purchaseOrderItemId` 分组 —— 避免逐行查库（N+1）。
     */
    public List<PurchaseOrderItemVO> itemVos(List<PurchaseOrderItemEntity> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> itemIds = rows.stream().map(PurchaseOrderItemEntity::getId).toList();
        List<PurchaseDemandAllocationEntity> allocations =
                purchaseDemandAllocationDao.listActiveByOrderItemIds(itemIds);
        Map<Long, PurchaseDemandEntity> demands = demandMap(allocations);

        Map<Long, List<PurchaseOrderAllocationVO>> byItem = new LinkedHashMap<>();
        for (PurchaseDemandAllocationEntity allocation : allocations) {
            byItem.computeIfAbsent(allocation.getPurchaseOrderItemId(), key -> new ArrayList<>())
                    .add(allocationVo(allocation, demands.get(allocation.getPurchaseDemandId())));
        }

        return rows.stream().sorted(
                        Comparator.comparing(PurchaseOrderItemEntity::getSortOrder,
                                        Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(PurchaseOrderItemEntity::getId))
                .map(row -> itemVo(row, byItem.getOrDefault(row.getId(), List.of())))
                .toList();
    }

    private static PurchaseOrderItemVO itemVo(PurchaseOrderItemEntity row,
                                              List<PurchaseOrderAllocationVO> allocations) {
        PurchaseOrderItemVO vo = new PurchaseOrderItemVO();
        vo.setId(row.getId());
        vo.setSkuId(row.getSkuId());
        vo.setSpuCode(row.getSpuCodeSnapshot());
        vo.setProductName(row.getProductNameSnapshot());
        vo.setSkuCode(row.getSkuCodeSnapshot());
        vo.setSkuName(row.getSkuNameSnapshot());
        vo.setSpecValues(row.getSpecValuesSnapshot());
        vo.setPurchaseUnit(row.getPurchaseUnitSnapshot());
        vo.setProductType(row.getProductTypeSnapshot());
        vo.setPlannedQuantity(row.getPlannedQuantity());
        vo.setReceivedQuantity(row.getReceivedQuantity());
        // P24：与 DB 的 ck_purchase_receipt_item_reconciliation 用同一个纯函数
        vo.setRemainingQuantity(PurchaseReceiptQuantityCalculator.remaining(
                row.getPlannedQuantity(), row.getReceivedQuantity()));
        vo.setOverReceiptQuantity(PurchaseReceiptQuantityCalculator.overReceipt(
                row.getPlannedQuantity(), row.getReceivedQuantity()));
        vo.setPurchasePrice(row.getPurchasePrice());
        vo.setLineAmount(row.getLineAmount());
        vo.setSortOrder(row.getSortOrder());
        vo.setVersion(row.getVersion());
        vo.setAllocations(allocations);
        return vo;
    }

    /**
     * 分配 VO。
     *
     * <p>`demandUnit` / `demandVersion` / `demandStatus` 取自**需求当前值**（不是分配行上的快照）：
     * 它们表达的是「这条分配现在挂在一个什么状态的需求上」，快照表达不了「需求已补齐」。
     * `quantity` 取分配行自己的数量（Q13：一条分配一个数量）。
     */
    private static PurchaseOrderAllocationVO allocationVo(PurchaseDemandAllocationEntity row,
                                                          PurchaseDemandEntity demand) {
        PurchaseOrderAllocationVO vo = new PurchaseOrderAllocationVO();
        vo.setAllocationId(row.getId());
        vo.setDemandId(row.getPurchaseDemandId());
        vo.setSalesOrderId(row.getSalesOrderId());
        vo.setSalesOrderItemId(row.getSalesOrderItemId());
        vo.setSkuId(row.getSkuId());
        vo.setQuantity(row.getAllocatedQuantity());
        if (demand != null) {
            vo.setSalesOrderNo(demand.getSalesOrderNoSnapshot());
            vo.setDemandUnit(demand.getDemandUnitSnapshot());
            vo.setDemandVersion(demand.getVersion());
            vo.setDemandStatus(demand.getStatus());
        }
        return vo;
    }

    private Map<Long, PurchaseDemandEntity> demandMap(List<PurchaseDemandAllocationEntity> allocations) {
        List<Long> demandIds = allocations.stream()
                .map(PurchaseDemandAllocationEntity::getPurchaseDemandId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (demandIds.isEmpty()) {
            return Map.of();
        }
        return purchaseDemandDao.selectBatchIds(demandIds).stream()
                .collect(Collectors.toMap(PurchaseDemandEntity::getId, demand -> demand, (a, b) -> a));
    }

    private static PurchaseReceiptItemVO receiptItemVo(PurchaseReceiptItemEntity row) {
        PurchaseReceiptItemVO vo = new PurchaseReceiptItemVO();
        vo.setId(row.getId());
        vo.setPurchaseOrderItemId(row.getPurchaseOrderItemId());
        vo.setSkuId(row.getSkuId());
        vo.setSkuCode(row.getSkuCodeSnapshot());
        vo.setSkuName(row.getSkuNameSnapshot());
        vo.setSpecValues(row.getSpecValuesSnapshot());
        vo.setPurchaseUnit(row.getPurchaseUnitSnapshot());
        vo.setProductType(row.getProductTypeSnapshot());
        vo.setPlannedQuantity(row.getPlannedQuantity());
        vo.setReceivedQuantity(row.getReceivedQuantity());
        vo.setCumulativeReceivedQuantity(row.getCumulativeReceivedQuantity());
        vo.setRemainingQuantity(row.getRemainingQuantity());
        vo.setOverReceiptQuantity(row.getOverReceiptQuantity());
        vo.setReceiptDifference(row.getReceiptDifference());
        // 标品为 null（不是 0.0000）：三字段同生同灭
        vo.setActualWeight(row.getActualWeight());
        vo.setWeightUnit(row.getWeightUnit());
        vo.setWeighingSource(row.getWeighingSource());
        vo.setCorrectionReason(row.getCorrectionReason());
        vo.setVersion(row.getVersion());
        return vo;
    }
}
