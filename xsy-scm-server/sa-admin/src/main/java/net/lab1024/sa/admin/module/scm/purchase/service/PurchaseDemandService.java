package net.lab1024.sa.admin.module.scm.purchase.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.scope.ScmValueScope;
import net.lab1024.sa.admin.module.scm.order.dao.SalesOrderDao;
import net.lab1024.sa.admin.module.scm.order.domain.entity.SalesOrderEntity;
import net.lab1024.sa.admin.module.scm.order.domain.entity.SalesOrderItemEntity;
import net.lab1024.sa.admin.module.scm.purchase.constant.ScmPurchaseOperationTypeEnum;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseDemandAllocationDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseDemandDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseOperationLogDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseOrderDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseOrderItemDao;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandAllocationEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseOrderEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseOrderItemEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseDemandAllocateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseDemandGenerateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseDemandVO;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseDemandAllocator;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseOrderValidator;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseSnapshotFactory;
import net.lab1024.sa.admin.module.scm.purchase.support.PurchaseDemandSourceGuard;
import net.lab1024.sa.admin.module.scm.purchase.support.PurchaseOwnerResolver;
import net.lab1024.sa.admin.module.scm.purchase.support.PurchaseWarehouseReferenceGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_DEMAND_NOT_FOUND;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_NOT_FOUND;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_ITEM_NOT_FOUND;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_STATE_INVALID;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_QUANTITY_INVALID;

/**
 * 采购需求命令服务（W5 Target Design §7.4 / §4.4）。
 *
 * <p>两个命令，各自一个事务、各自一个幂等 scope（§7.11）：
 * <ul>
 *   <li>{@code generate}：窗口内**已确认**订单行 → 需求。去重靠
 *       `uk_purchase_demand_source_active` + INSERT 竞争（C1：并发 generate 收敛到同一行）；</li>
 *   <li>{@code allocate}：把某条需求分到某个采购行上。**Q17**：需求单位与采购单位必须一致，
 *       否则 40971 —— 绝不猜换算系数。</li>
 * </ul>
 *
 * <p><b>锁序（P12）</b>：本类只锁 `purchase_demand`（单条，或按 id 升序）。
 * 它是全局锁序的第 1 层，因此**不会**与 `receipt.confirm`（2→3→4→5）交叉成环。
 */
@Service
@RequiredArgsConstructor
public class PurchaseDemandService {

    private static final String SCOPE_GENERATE = "PURCHASE_DEMAND_GENERATE";

    private final PurchaseDemandDao purchaseDemandDao;

    private final PurchaseDemandAllocationDao purchaseDemandAllocationDao;

    private final PurchaseOrderDao purchaseOrderDao;

    private final PurchaseOrderItemDao purchaseOrderItemDao;

    private final PurchaseOperationLogDao purchaseOperationLogDao;

    private final SalesOrderDao salesOrderDao;

    private final PurchaseQueryService queryService;

    private final PurchaseIdempotencyService idempotencyService;

    private final PurchaseWarehouseReferenceGuard warehouseReferenceGuard;

    private final PurchaseOrderValidator purchaseOrderValidator;

    private final PurchaseOwnerResolver ownerResolver;

    /**
     * `generate` 的返回体。
     *
     * <p>与 §7.12 的 `DEMAND_GENERATE` 日志 `after_data` 同构 —— 日志与返回值不是两套口径。
     * `skippedCount` 统计的是「来源行已存在活动需求」的数量（重复 generate 的正常结果，不是错误）。
     */
    @Data
    public static class GenerateResult {
        private List<Long> demandIds = new ArrayList<>();
        private int sourceLineCount;
        private int createdCount;
        private int skippedCount;
    }

    // ------------------------------------------------------------------
    // demand.generate
    // ------------------------------------------------------------------

    /**
     * 汇总窗口内的已确认订单行 → 采购需求。
     *
     * <p>半开区间 {@code [startAt, endAt)}；`demand_date` 按 **Asia/Shanghai** 取
     * `sales_order.confirmed_at` 的日期（Q6a），**不是** `date(startAt)`。
     */
    @Transactional(rollbackFor = Exception.class)
    public GenerateResult generate(PurchaseDemandGenerateForm form, String idempotencyKey) {
        var claim = idempotencyService.claim(SCOPE_GENERATE, idempotencyKey, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, GenerateResult.class);
        }

        validateWindow(form);
        warehouseReferenceGuard.requireEnabled(form.getWarehouseId());
        if (form.getSupplierId() != null) {
            purchaseOrderValidator.requireEnabledSupplier(form.getSupplierId());
        }

        // 命令取数不按仓库范围收窄：来源行是销售订单行，本身不带仓库，本次需求落哪个仓由
        // form.warehouseId 显式给出并由 warehouseReferenceGuard 校验启用态；「这个仓归不归他管」
        // 是收货上架 / 出库这些改库存事实的写路径的判据（ScmWarehouseScopeGuard），不在这里重复。
        // 传 all() 而不是省略参数，是为了让 Dao 的 scope 语义在两处调用点都显式可查。
        List<SalesOrderItemEntity> sourceItems =
                purchaseDemandDao.listSourceItems(form.getStartAt(), form.getEndAt(), ScmValueScope.all());

        GenerateResult result = new GenerateResult();
        result.setSourceLineCount(sourceItems.size());

        if (!sourceItems.isEmpty()) {
            Map<Long, SalesOrderEntity> orders = ordersOf(sourceItems);
            Map<Long, Long> existingDemandIds = existingDemandIds(sourceItems);

            for (SalesOrderItemEntity source : sourceItems) {
                SalesOrderEntity order = orders.get(source.getOrderId());
                // 40980：来源订单必须已确认且未软删。SQL 已按 status 过滤，这里是**不变量断言** ——
                // 口径（取哪些行）与不变量（取到的行是否可信）是两层防线，不允许被合并。
                PurchaseDemandSourceGuard.requireConfirmed(order);

                Long existing = existingDemandIds.get(source.getId());
                if (existing != null) {
                    result.getDemandIds().add(existing);
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    continue;
                }

                PurchaseDemandEntity demand = PurchaseSnapshotFactory.demand(
                        source, order, form.getSupplierId(), form.getWarehouseId(),
                        // 需求上的采购员同样是归属依据（generate 的「默认采购员」只是建议值）：
                        // 无分配权时一律落成当前员工，否则列表范围可以被一个表单字段放大
                        ownerResolver.resolveForCreate(form.getPurchaserId()));
                if (purchaseDemandDao.insertIgnore(demand) == 1) {
                    result.getDemandIds().add(demand.getId());
                    result.setCreatedCount(result.getCreatedCount() + 1);
                } else {
                    // 并发对手先插成功：重读收敛到同一行（C1），本次计为 skipped 而不是失败
                    Long winner = reReadDemandId(source.getId());
                    result.getDemandIds().add(winner);
                    result.setSkippedCount(result.getSkippedCount() + 1);
                }
            }
        }

        Map<String, Object> after = PurchaseSnapshotFactory.snapshot();
        after.put("demandIds", result.getDemandIds());
        after.put("sourceLineCount", result.getSourceLineCount());
        after.put("createdCount", result.getCreatedCount());
        after.put("skippedCount", result.getSkippedCount());
        // Q14：DEMAND_GENERATE 时采购单与收货单都还不存在 → 两个 id 都必须为 NULL
        purchaseOperationLogDao.append(PurchaseSnapshotFactory.operationLog(
                ScmPurchaseOperationTypeEnum.DEMAND_GENERATE, null, null, null, null, after));

        idempotencyService.complete(claim, "PURCHASE_DEMAND_GENERATE", null, result);
        return result;
    }

    // ------------------------------------------------------------------
    // demand.allocate
    // ------------------------------------------------------------------

    /**
     * 把一条需求分配到某个采购行上（Q13：一次一条 allocation；Q17：单位必须一致）。
     *
     * <p>需求侧 `allocated_quantity` 是**跨采购单累计值**，因此这里读的是需求行自身的
     * `allocated_quantity`（已在锁内），加上本次数量后回写。
     */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseDemandVO allocate(PurchaseDemandAllocateForm form, String idempotencyKey) {
        var claim = idempotencyService.claim(
                "PURCHASE_DEMAND_ALLOCATE:" + form.getDemandId(), idempotencyKey, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, PurchaseDemandVO.class);
        }

        // 锁序第 1 层：purchase_demand（单条）
        PurchaseDemandEntity demand = purchaseDemandDao.lock(form.getDemandId());
        if (demand == null) {
            throw new ScmBusinessException(PURCHASE_DEMAND_NOT_FOUND);
        }
        PurchaseDemandAllocator.assignable(demand.getStatus());
        PurchaseDemandAllocator.demandVersion(form.getVersion(), demand.getVersion());
        // 归属守卫：分配会把需求量并进别人名下的采购单，两头都必须在调用者范围内。
        // 只判需求会留下一条侧门——用自己的采购单接住别人的需求，两边数字同时被改。
        ownerResolver.requireVisible(demand.getPurchaserId());

        PurchaseOrderItemEntity orderItem = purchaseOrderItemDao.selectById(form.getPurchaseOrderItemId());
        if (orderItem == null) {
            throw new ScmBusinessException(PURCHASE_ORDER_ITEM_NOT_FOUND);
        }
        PurchaseOrderEntity order = purchaseOrderDao.selectById(orderItem.getPurchaseOrderId());
        if (order == null) {
            throw new ScmBusinessException(PURCHASE_ORDER_NOT_FOUND);
        }
        ownerResolver.requireVisible(order.getPurchaserId());
        // 只有 SUBMITTED 的采购单可以继续接需求：DRAFT 还没定稿，RECEIVED/SHORT_CLOSED/CANCELLED 已结束。
        // 这里沿用设计 §7.4 指定的 40980（PURCHASE_DEMAND_SOURCE_INVALID）——
        // 语义上「来源不允许再产生/变更需求关联」，与来源订单状态校验同源。
        if (!"SUBMITTED".equals(order.getStatus())) {
            throw new ScmBusinessException(PURCHASE_ORDER_STATE_INVALID);
        }

        // Q17：需求单位（销售单位快照）必须等于采购单位（supplier_sku.purchase_unit 快照）
        PurchaseDemandAllocator.unitCompatible(
                demand.getDemandUnitSnapshot(), orderItem.getPurchaseUnitSnapshot());
        // 同一 SKU 才能挂
        PurchaseDemandAllocator.itemMatchesDemand(orderItem.getSkuId(), demand.getSkuId());
        // 仓库必须在 generate 时已固定且一致；供应商由首次分配固定
        PurchaseDemandAllocator.assignmentCompatible(order.getSupplierId(), order.getWarehouseId(), demand);

        BigDecimal quantity = PurchaseOrderValidator.decimal(form.getQuantity(), true);
        BigDecimal previousAllocated = demand.getAllocatedQuantity();
        BigDecimal finalAllocated = previousAllocated.add(quantity);
        PurchaseDemandAllocator.withinRequired(demand.getRequiredQuantity(), finalAllocated);

        PurchaseDemandAllocationEntity allocation = new PurchaseDemandAllocationEntity();
        allocation.setPurchaseDemandId(demand.getId());
        allocation.setPurchaseOrderItemId(orderItem.getId());
        allocation.setSalesOrderId(demand.getSalesOrderId());
        allocation.setSalesOrderItemId(demand.getSalesOrderItemId());
        allocation.setSkuId(demand.getSkuId());
        allocation.setAllocatedQuantity(quantity);
        allocation.setDemandSnapshot(PurchaseSnapshotFactory.allocationDemandSnapshot(demand));
        allocation.setVersion(0);
        allocation.setDeleted(false);
        allocation.setCreatedBy(ScmOperator.current());
        purchaseDemandAllocationDao.insert(allocation);

        Long supplierId = PurchaseDemandAllocator.shouldFixSupplier(demand)
                ? order.getSupplierId()
                : demand.getSupplierId();
        String status = PurchaseDemandAllocator.statusFor(demand.getRequiredQuantity(), finalAllocated);
        if (purchaseDemandDao.updateAllocation(demand.getId(), demand.getVersion(),
                finalAllocated, status, supplierId, ScmOperator.current()) != 1) {
            throw new ScmBusinessException(
                    net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT);
        }

        // Q14：DEMAND_ALLOCATE 的 purchase_order_id 由 purchaseOrderItemId **反查**得到，非空；
        // purchase_receipt_id 为 NULL。ck_purchase_operation_log_owner 会复核这一分支。
        Map<String, Object> before = PurchaseSnapshotFactory.snapshot();
        before.put("allocatedQuantity", PurchaseSnapshotFactory.fixed(previousAllocated));
        Map<String, Object> after = PurchaseSnapshotFactory.snapshot();
        after.put("purchaseOrderItemId", orderItem.getId());
        after.put("purchaseOrderId", order.getId());
        Map<String, Object> one = PurchaseSnapshotFactory.snapshot();
        one.put("demandId", demand.getId());
        one.put("quantity", PurchaseSnapshotFactory.fixed(quantity));
        after.put("allocations", List.of(one));
        after.put("allocatedQuantity", PurchaseSnapshotFactory.fixed(finalAllocated));
        after.put("supplierId", supplierId);
        after.put("warehouseId", demand.getWarehouseId());
        purchaseOperationLogDao.append(PurchaseSnapshotFactory.operationLog(
                ScmPurchaseOperationTypeEnum.DEMAND_ALLOCATE, order.getId(), null, null, before, after));

        PurchaseDemandVO result = queryService.demandDetailForCommand(demand.getId());
        idempotencyService.complete(claim, "PURCHASE_DEMAND", demand.getId(), result);
        return result;
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private static void validateWindow(PurchaseDemandGenerateForm form) {
        OffsetDateTime startAt = form.getStartAt();
        OffsetDateTime endAt = form.getEndAt();
        if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {
            // 半开区间要求 startAt < endAt；相等会得到空集，是调用方的 bug 而不是「无数据」
            throw new ScmBusinessException(PURCHASE_QUANTITY_INVALID);
        }
    }

    private Map<Long, SalesOrderEntity> ordersOf(List<SalesOrderItemEntity> sourceItems) {
        List<Long> orderIds = sourceItems.stream()
                .map(SalesOrderItemEntity::getOrderId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        // 一次批量取（不是逐行查库）：来源行可能上百条，N+1 会明显拖慢 generate
        return salesOrderDao.selectBatchIds(orderIds).stream()
                .collect(Collectors.toMap(SalesOrderEntity::getId, order -> order, (a, b) -> a));
    }

    private Map<Long, Long> existingDemandIds(List<SalesOrderItemEntity> sourceItems) {
        List<Long> itemIds = sourceItems.stream().map(SalesOrderItemEntity::getId).toList();
        Map<Long, Long> bySourceItem = new LinkedHashMap<>();
        for (PurchaseDemandEntity row : purchaseDemandDao.listActiveBySourceItemIds(itemIds)) {
            bySourceItem.put(row.getSalesOrderItemId(), row.getId());
        }
        return bySourceItem;
    }

    private Long reReadDemandId(Long salesOrderItemId) {
        List<PurchaseDemandEntity> rows =
                purchaseDemandDao.listActiveBySourceItemIds(List.of(salesOrderItemId));
        if (rows.isEmpty()) {
            // INSERT 报告冲突却读不到行：唯一索引与查询条件不一致，属数据/映射缺陷
            throw new IllegalStateException(
                    "采购需求 INSERT 冲突但重读为空，salesOrderItemId=" + salesOrderItemId);
        }
        return rows.get(0).getId();
    }
}
