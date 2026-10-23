package com.xsy.scm.purchase.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.order.dao.SalesOrderDao;
import com.xsy.scm.order.domain.entity.SalesOrderEntity;
import com.xsy.scm.order.domain.entity.SalesOrderItemEntity;
import com.xsy.scm.purchase.constant.ScmPurchaseOperationTypeEnum;
import com.xsy.scm.purchase.constant.ScmPurchaseStatusEnum;
import com.xsy.scm.purchase.dao.PurchaseDemandAllocationDao;
import com.xsy.scm.purchase.dao.PurchaseDemandDao;
import com.xsy.scm.purchase.dao.PurchaseOperationLogDao;
import com.xsy.scm.purchase.dao.PurchaseOrderDao;
import com.xsy.scm.purchase.dao.PurchaseOrderItemDao;
import com.xsy.scm.purchase.domain.entity.PurchaseDemandAllocationEntity;
import com.xsy.scm.purchase.domain.entity.PurchaseDemandEntity;
import com.xsy.scm.purchase.domain.entity.PurchaseOrderEntity;
import com.xsy.scm.purchase.domain.entity.PurchaseOrderItemEntity;
import com.xsy.scm.purchase.domain.form.PurchaseDemandAllocateForm;
import com.xsy.scm.purchase.domain.form.PurchaseDemandGenerateForm;
import com.xsy.scm.purchase.domain.vo.PurchaseDemandVO;
import com.xsy.scm.purchase.manager.PurchaseDemandAllocator;
import com.xsy.scm.purchase.manager.PurchaseOrderValidator;
import com.xsy.scm.purchase.manager.PurchaseSnapshotFactory;
import com.xsy.scm.purchase.support.PurchaseDemandSourceGuard;
import com.xsy.scm.purchase.support.PurchaseOwnerResolver;
import com.xsy.scm.purchase.support.PurchaseWarehouseReferenceGuard;
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

import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_DEMAND_NOT_FOUND;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_NOT_FOUND;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_ITEM_NOT_FOUND;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_STATE_INVALID;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_QUANTITY_INVALID;

/**
 * 根据已确认订单生成采购需求，并把需求分配到采购单。生成时按来源去重；分配时要求需求单位与采购单位一致，不自动换算。 分配先锁采购单，再锁需求记录，避免与修改、取消采购单发生并发冲突。
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

    private final PurchaseQueryService purchaseQueryService;

    private final PurchaseIdempotencyService purchaseIdempotencyService;

    private final PurchaseWarehouseReferenceGuard purchaseWarehouseReferenceGuard;

    private final PurchaseOrderValidator purchaseOrderValidator;

    private final PurchaseOwnerResolver purchaseOwnerResolver;

    /**
     * {@code generate} 的返回体。
     *
     * <p>
     * 与 {@code DEMAND_GENERATE} 日志 {@code after_data} 结构一致，日志与返回值共用一套数据。{@code skippedCount} 统计的是 「来源行已存在活动需求」的数量（重复
     * generate 的正常结果，不是错误）。
     */
    @Data
    public static class GenerateResult {
        private List<Long> demandIds = new ArrayList<>();
        private int sourceLineCount;
        private int createdCount;
        private int skippedCount;
    }

    /**
     * 汇总窗口内的已确认订单行 → 采购需求。
     *
     * <p>
     * 半开区间 {@code [startAt, endAt)}；{@code demand_date} 按 Asia/Shanghai 取 {@code xsy_sales_order.confirmed_at} 的日期，不是
     * {@code date(startAt)}。
     */
    @Transactional(rollbackFor = Exception.class)
    public GenerateResult generate(PurchaseDemandGenerateForm form, String idempotencyKey) {
        var claim = purchaseIdempotencyService.claim(SCOPE_GENERATE, idempotencyKey, form);
        if (claim.replay()) {
            return purchaseIdempotencyService.replay(claim, GenerateResult.class);
        }

        validateWindow(form);
        purchaseWarehouseReferenceGuard.requireEnabled(form.getWarehouseId());
        if (form.getSupplierId() != null) {
            purchaseOrderValidator.requireEnabledSupplier(form.getSupplierId());
        }

        // 命令取数不按仓库范围收窄：来源行是销售订单行，本身不带仓库，本次需求落哪个仓由
        // form.warehouseId 显式给出，并由 warehouseReferenceGuard 校验启用态；仓库归属判定属于
        // 收货上架、出库这些改库存的写路径（ScmWarehouseScopeGuard），不在这里重复。
        // 传 all() 而不是省略参数，是为了让 Dao 的 scope 语义在调用点显式可查。
        List<SalesOrderItemEntity> sourceItems = purchaseDemandDao.listSourceItems(form.getStartAt(), form.getEndAt(),
            ScmValueScope.all());

        GenerateResult result = new GenerateResult();
        result.setSourceLineCount(sourceItems.size());

        if (!sourceItems.isEmpty()) {
            Map<Long, SalesOrderEntity> orders = ordersOf(sourceItems);
            Map<Long, Long> existingDemandIds = existingDemandIds(sourceItems);

            for (SalesOrderItemEntity source : sourceItems) {
                SalesOrderEntity order = orders.get(source.getOrderId());
                // 40980：来源订单必须已确认且未软删。SQL 已按 status 过滤，这里是补充断言：
                // 取哪些行（查询条件）与取到的行是否可信是两层防线，不能合并。
                PurchaseDemandSourceGuard.requireConfirmed(order);

                Long existing = existingDemandIds.get(source.getId());
                if (existing != null) {
                    result.getDemandIds().add(existing);
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    continue;
                }

                PurchaseDemandEntity demand = PurchaseSnapshotFactory.demand(source, order, form.getSupplierId(),
                    form.getWarehouseId(),
                    // 需求上的采购员同样是归属依据（generate 的「默认采购员」只是建议值）：
                    // 无分配权时一律记为当前员工，否则列表范围可以被一个表单字段放大
                    purchaseOwnerResolver.resolveForCreate(form.getPurchaserId()));
                if (purchaseDemandDao.insertIgnore(demand) == 1) {
                    result.getDemandIds().add(demand.getId());
                    result.setCreatedCount(result.getCreatedCount() + 1);
                } else {
                    // 并发对手先插成功：重读收敛到同一行，本次计为 skipped 而不是失败
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
        // DEMAND_GENERATE 时采购单与收货单都还不存在 → 两个 id 都必须为 NULL
        purchaseOperationLogDao.append(PurchaseSnapshotFactory
            .operationLog(ScmPurchaseOperationTypeEnum.DEMAND_GENERATE, null, null, null, null, after));

        purchaseIdempotencyService.complete(claim, "PURCHASE_DEMAND_GENERATE", null, result);
        return result;
    }

    /**
     * 把一条需求分配到某个采购行上（一次一条分配，单位必须一致）。
     *
     * <p>
     * 需求侧 {@code allocated_quantity} 是跨采购单累计值，因此这里读的是需求行自身的 {@code allocated_quantity}（已在锁内）， 加上本次数量后回写。
     */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseDemandVO allocate(PurchaseDemandAllocateForm form, String idempotencyKey) {
        var claim = purchaseIdempotencyService.claim("PURCHASE_DEMAND_ALLOCATE:" + form.getDemandId(), idempotencyKey,
            form);
        if (claim.replay()) {
            return purchaseIdempotencyService.replay(claim, PurchaseDemandVO.class);
        }

        // 锁序与 PurchaseOrderService 的 update / cancel / delete 一致：采购单 → 需求。
        // 先取采购单行锁后，cancel 与 allocate 在单据行上串行；否则 cancel 读到「本单没有分配」提前返回，
        // allocate 随后插入并提交，分配就会永久挂在已取消的单上、需求的 allocated_quantity 也不回落。
        PurchaseOrderItemEntity orderItem = purchaseOrderItemDao.selectById(form.getPurchaseOrderItemId());
        if (orderItem == null) {
            throw new ScmBusinessException(PURCHASE_ORDER_ITEM_NOT_FOUND);
        }
        PurchaseOrderEntity order = purchaseOrderDao.lock(orderItem.getPurchaseOrderId());
        if (order == null) {
            throw new ScmBusinessException(PURCHASE_ORDER_NOT_FOUND);
        }
        requireSubmitted(order.getStatus());
        purchaseOwnerResolver.requireVisible(order.getPurchaserId());

        PurchaseDemandEntity demand = purchaseDemandDao.lock(form.getDemandId());
        if (demand == null) {
            throw new ScmBusinessException(PURCHASE_DEMAND_NOT_FOUND);
        }
        PurchaseDemandAllocator.assignable(demand.getStatus());
        PurchaseDemandAllocator.demandVersion(form.getVersion(), demand.getVersion());
        // 归属校验：分配会把需求量并进别人名下的采购单，两头都必须在调用者范围内；
        // 只判需求会留下侧门——用自己的采购单接住别人的需求，两边数字同时被改。
        purchaseOwnerResolver.requireVisible(demand.getPurchaserId());

        // 需求单位（销售单位快照）必须等于采购单位（supplier_sku.purchase_unit 快照）
        PurchaseDemandAllocator.unitCompatible(demand.getDemandUnitSnapshot(), orderItem.getPurchaseUnitSnapshot());
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
        if (purchaseDemandDao.updateAllocation(demand.getId(), demand.getVersion(), finalAllocated, status, supplierId,
            ScmOperator.current()) != 1) {
            throw new ScmBusinessException(com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT);
        }

        // DEMAND_ALLOCATE 的 purchase_order_id 由 purchaseOrderItemId 反查得到，非空；
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
        purchaseOperationLogDao.append(PurchaseSnapshotFactory
            .operationLog(ScmPurchaseOperationTypeEnum.DEMAND_ALLOCATE, order.getId(), null, null, before, after));

        PurchaseDemandVO result = purchaseQueryService.demandDetailForCommand(demand.getId());
        purchaseIdempotencyService.complete(claim, "PURCHASE_DEMAND", demand.getId(), result);
        return result;
    }

    /**
     * 采购单状态必须是 {@code SUBMITTED}（{@code null} 视为单据不存在）。
     *
     * <p>
     * 抽成独立方法是为了让「单据不存在」与「状态不允许」两种拒绝各自对应明确的对外码 （40480 / 40982），而不是把两者揉成一个布尔判定。
     */
    private static void requireSubmitted(String status) {
        if (status == null) {
            throw new ScmBusinessException(PURCHASE_ORDER_NOT_FOUND);
        }
        // 只有 SUBMITTED 的采购单可以继续接需求：DRAFT 还没定稿，RECEIVED/SHORT_CLOSED/CANCELLED 已结束。
        if (!ScmPurchaseStatusEnum.SUBMITTED.name().equals(status)) {
            throw new ScmBusinessException(PURCHASE_ORDER_STATE_INVALID);
        }
    }

    private static void validateWindow(PurchaseDemandGenerateForm form) {
        OffsetDateTime startAt = form.getStartAt();
        OffsetDateTime endAt = form.getEndAt();
        if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {
            // 半开区间要求 startAt < endAt；相等会得到空集，是调用方的 bug 而不是「无数据」
            throw new ScmBusinessException(PURCHASE_QUANTITY_INVALID);
        }
    }

    private Map<Long, SalesOrderEntity> ordersOf(List<SalesOrderItemEntity> sourceItems) {
        List<Long> orderIds = sourceItems.stream().map(SalesOrderItemEntity::getOrderId).filter(Objects::nonNull)
            .distinct().toList();
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
        List<PurchaseDemandEntity> rows = purchaseDemandDao.listActiveBySourceItemIds(List.of(salesOrderItemId));
        if (rows.isEmpty()) {
            // INSERT 报告冲突却读不到行：唯一索引与查询条件不一致，属数据/映射缺陷
            throw new IllegalStateException("采购需求 INSERT 冲突但重读为空，salesOrderItemId=" + salesOrderItemId);
        }
        return rows.get(0).getId();
    }
}
