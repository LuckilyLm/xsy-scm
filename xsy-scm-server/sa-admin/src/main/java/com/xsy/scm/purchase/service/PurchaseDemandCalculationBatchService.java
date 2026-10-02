package com.xsy.scm.purchase.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.order.dao.SalesOrderDao;
import com.xsy.scm.order.domain.entity.SalesOrderEntity;
import com.xsy.scm.order.domain.entity.SalesOrderItemEntity;
import com.xsy.scm.purchase.constant.PurchaseErrorCode;
import com.xsy.scm.purchase.dao.PurchaseDemandCalculationBatchDao;
import com.xsy.scm.purchase.dao.PurchaseDemandCalculationBatchItemDao;
import com.xsy.scm.purchase.dao.PurchaseDemandDao;
import com.xsy.scm.purchase.domain.entity.PurchaseDemandCalculationBatchEntity;
import com.xsy.scm.purchase.domain.entity.PurchaseDemandCalculationBatchItemEntity;
import com.xsy.scm.purchase.domain.entity.PurchaseDemandEntity;
import com.xsy.scm.purchase.domain.form.PurchaseDemandBatchCreateForm;
import com.xsy.scm.purchase.domain.form.PurchaseDemandBatchGenerateForm;
import com.xsy.scm.purchase.domain.form.PurchaseDemandSummaryPreviewForm;
import com.xsy.scm.purchase.domain.vo.PurchaseDemandCalculationBatchDetailVO;
import com.xsy.scm.purchase.domain.vo.PurchaseDemandCalculationBatchItemVO;
import com.xsy.scm.purchase.domain.vo.PurchaseDemandCalculationBatchVO;
import com.xsy.scm.purchase.manager.PurchaseSnapshotFactory;
import com.xsy.scm.purchase.support.PurchaseOwnerResolver;
import com.xsy.scm.purchase.support.PurchaseWarehouseReferenceGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Freezes the read-side net-demand calculation before any demand write occurs. */
@Service
@RequiredArgsConstructor
public class PurchaseDemandCalculationBatchService {
    private static final String CREATE_SCOPE = "PURCHASE_DEMAND_CALCULATION_BATCH_CREATE";
    private static final String GENERATE_SCOPE = "PURCHASE_DEMAND_CALCULATION_BATCH_GENERATE";
    private static final int MAX_ROWS = 5000;

    private final PurchaseDemandCalculationBatchDao batchDao;
    private final PurchaseDemandCalculationBatchItemDao batchItemDao;
    private final PurchaseDemandDao purchaseDemandDao;
    private final PurchaseQueryService purchaseQueryService;
    private final SalesOrderDao salesOrderDao;
    private final PurchaseIdempotencyService idempotencyService;
    private final PurchaseWarehouseReferenceGuard warehouseReferenceGuard;
    private final PurchaseOwnerResolver ownerResolver;
    private final ScmDataScopeService dataScopeService;

    @Transactional(rollbackFor = Exception.class)
    public PurchaseDemandCalculationBatchVO create(PurchaseDemandBatchCreateForm form, String key) {
        var claim = idempotencyService.claim(CREATE_SCOPE, key, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, PurchaseDemandCalculationBatchVO.class);
        }
        validateWindow(form.getStartAt(), form.getEndAt());
        warehouseReferenceGuard.requireEnabled(form.getWarehouseId());
        if (form.getPurchaserId() != null) {
            ownerResolver.requireVisible(form.getPurchaserId());
        }

        PurchaseDemandSummaryPreviewForm previewForm = new PurchaseDemandSummaryPreviewForm();
        previewForm.setStartAt(form.getStartAt());
        previewForm.setEndAt(form.getEndAt());
        previewForm.setWarehouseId(form.getWarehouseId());
        previewForm.setCategoryId(form.getCategoryId());
        previewForm.setKeyword(form.getKeyword());
        var summary = purchaseQueryService.summaryPreviewAll(previewForm, MAX_ROWS);
        Map<String, BigDecimal> gaps = summary.stream().collect(Collectors.toMap(
                row -> key(row.getSkuId(), row.getDemandUnit()),
                row -> row.getNetPurchaseGap() == null ? BigDecimal.ZERO : row.getNetPurchaseGap(),
                BigDecimal::add, LinkedHashMap::new));

        List<SalesOrderItemEntity> sourceItems = purchaseDemandDao.listSourceItems(form.getStartAt(), form.getEndAt(),
                ScmValueScope.all());
        Map<Long, SalesOrderEntity> orders = salesOrderDao.selectBatchIds(sourceItems.stream()
                .map(SalesOrderItemEntity::getOrderId).distinct().toList()).stream()
                .collect(Collectors.toMap(SalesOrderEntity::getId, Function.identity()));
        Map<Long, PurchaseDemandEntity> existing = existing(sourceItems);

        // 批次头记录**解析后**的归属，与稍后由同一批次生成的需求保持同一个 owner：
        // 只存表单原值会让「没分配权的采购员建了自己的批次却读不回来」。
        Long resolvedPurchaserId = ownerResolver.resolveForCreate(form.getPurchaserId());

        PurchaseDemandCalculationBatchEntity batch = new PurchaseDemandCalculationBatchEntity();
        batch.setStartAt(form.getStartAt());
        batch.setEndAt(form.getEndAt());
        batch.setWarehouseId(form.getWarehouseId());
        batch.setSupplierId(form.getSupplierId());
        batch.setPurchaserId(resolvedPurchaserId);
        batch.setCategoryId(form.getCategoryId());
        batch.setKeyword(form.getKeyword());
        batch.setStatus("READY");
        batch.setSourceLineCount(sourceItems.size());
        batch.setCreatedAt(OffsetDateTime.now());
        batch.setCreatedBy(ScmOperator.current());
        // V78 的 ck_purchase_demand_batch_summary 要求 summary_snapshot 是 JSON 对象而不是数组，
        // 因此解释行整体挂在 "rows" 键下；数字全部在这里定成四位定点字符串，回看时不再重算。
        Map<String, Object> summarySnapshot = PurchaseSnapshotFactory.snapshot();
        summarySnapshot.put("rows", summary.stream().map(row -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("skuId", row.getSkuId());
            value.put("skuCode", row.getSkuCode());
            value.put("productName", row.getProductName());
            value.put("skuName", row.getSkuName());
            value.put("demandUnit", row.getDemandUnit());
            value.put("orderDemandQuantity", PurchaseSnapshotFactory.fixed(row.getOrderDemandQuantity()));
            value.put("onHandQuantity", PurchaseSnapshotFactory.fixed(row.getOnHandQuantity()));
            value.put("reservedQuantity", PurchaseSnapshotFactory.fixed(row.getReservedQuantity()));
            value.put("selectedOrderReservedQuantity",
                    PurchaseSnapshotFactory.fixed(row.getSelectedOrderReservedQuantity()));
            value.put("otherReservedQuantity", PurchaseSnapshotFactory.fixed(row.getOtherReservedQuantity()));
            value.put("availableQuantity", PurchaseSnapshotFactory.fixed(row.getAvailableQuantity()));
            value.put("stockAvailableForSelectedOrders",
                    PurchaseSnapshotFactory.fixed(row.getStockAvailableForSelectedOrders()));
            value.put("stockComparisonGap", PurchaseSnapshotFactory.fixed(row.getStockComparisonGap()));
            value.put("inTransitQuantity", PurchaseSnapshotFactory.fixed(row.getInTransitQuantity()));
            value.put("purchaseCoverageQuantity", PurchaseSnapshotFactory.fixed(row.getPurchaseCoverageQuantity()));
            value.put("netPurchaseGap", PurchaseSnapshotFactory.fixed(row.getNetPurchaseGap()));
            value.put("calculationStatus", row.getCalculationStatus());
            return value;
        }).toList());
        batch.setSummarySnapshot(summarySnapshot);

        List<PurchaseDemandCalculationBatchItemEntity> items = new ArrayList<>();
        int lineNo = 1;
        for (SalesOrderItemEntity source : sourceItems) {
            SalesOrderEntity order = orders.get(source.getOrderId());
            if (order == null || source.getActualQuantity() == null || source.getActualQuantity().signum() <= 0) {
                continue;
            }
            PurchaseDemandEntity old = existing.get(source.getId());
            BigDecimal required = old == null ? consume(gaps, key(source.getSkuId(), source.getSaleUnitSnapshot()),
                    source.getActualQuantity()) : BigDecimal.ZERO.setScale(4);
            PurchaseDemandCalculationBatchItemEntity item = new PurchaseDemandCalculationBatchItemEntity();
            item.setBatchId(null);
            item.setLineNo(lineNo++);
            item.setSalesOrderId(order.getId());
            item.setSalesOrderItemId(source.getId());
            item.setSalesOrderNoSnapshot(order.getOrderNo());
            item.setSourceConfirmedAt(order.getConfirmedAt());
            item.setSpuId(source.getSpuId());
            item.setSkuId(source.getSkuId());
            item.setSpuCodeSnapshot(source.getSpuCodeSnapshot());
            item.setProductNameSnapshot(source.getProductNameSnapshot());
            item.setSkuCodeSnapshot(source.getSkuCodeSnapshot());
            item.setSkuNameSnapshot(source.getProductNameSnapshot());
            item.setSpecValuesSnapshot(PurchaseSnapshotFactory.copySpecValues(source.getSpecValuesSnapshot()));
            item.setDemandUnitSnapshot(source.getSaleUnitSnapshot());
            item.setProductTypeSnapshot(source.getProductTypeSnapshot());
            item.setSourceQuantity(source.getActualQuantity());
            item.setRequiredQuantity(required);
            item.setExistingDemandId(old == null ? null : old.getId());
            items.add(item);
        }
        batch.setCandidateLineCount(
                (int) items.stream().filter(item -> item.getRequiredQuantity().signum() > 0).count());
        batchDao.insert(batch);
        items.forEach(item -> item.setBatchId(batch.getId()));
        if (!items.isEmpty()) {
            batchItemDao.insertBatch(items);
        }
        PurchaseDemandCalculationBatchVO result = toVO(batch);
        idempotencyService.complete(claim, "PURCHASE_DEMAND_CALCULATION_BATCH", batch.getId(), result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseDemandService.GenerateResult generate(PurchaseDemandBatchGenerateForm form, String key) {
        var claim = idempotencyService.claim(GENERATE_SCOPE + ":" + form.getBatchId(), key, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, PurchaseDemandService.GenerateResult.class);
        }
        PurchaseDemandCalculationBatchEntity batch = batchDao.lock(form.getBatchId());
        if (batch == null) {
            throw new ScmBusinessException(PurchaseErrorCode.PURCHASE_DEMAND_BATCH_NOT_FOUND);
        }
        if (!"READY".equals(batch.getStatus())) {
            throw new ScmBusinessException(PurchaseErrorCode.PURCHASE_DEMAND_ALLOCATION_CONFLICT);
        }
        List<PurchaseDemandCalculationBatchItemEntity> items = batchItemDao.listByBatchId(batch.getId());
        Map<Long, SalesOrderEntity> orders = salesOrderDao.selectBatchIds(items.stream()
                .map(PurchaseDemandCalculationBatchItemEntity::getSalesOrderId).distinct().toList()).stream()
                .collect(Collectors.toMap(SalesOrderEntity::getId, Function.identity()));
        PurchaseDemandService.GenerateResult result = new PurchaseDemandService.GenerateResult();
        result.setSourceLineCount(items.size());
        for (PurchaseDemandCalculationBatchItemEntity item : items) {
            if (item.getRequiredQuantity() == null || item.getRequiredQuantity().signum() <= 0) {
                result.setSkippedCount(result.getSkippedCount() + 1);
                continue;
            }
            SalesOrderEntity order = orders.get(item.getSalesOrderId());
            SalesOrderItemEntity source = new SalesOrderItemEntity();
            source.setId(item.getSalesOrderItemId());
            source.setOrderId(item.getSalesOrderId());
            source.setSpuId(item.getSpuId());
            source.setSkuId(item.getSkuId());
            source.setSpuCodeSnapshot(item.getSpuCodeSnapshot());
            source.setProductNameSnapshot(item.getProductNameSnapshot());
            source.setSkuCodeSnapshot(item.getSkuCodeSnapshot());
            source.setSpecNameSnapshot(item.getSkuNameSnapshot());
            source.setSpecValuesSnapshot(item.getSpecValuesSnapshot());
            source.setSaleUnitSnapshot(item.getDemandUnitSnapshot());
            source.setProductTypeSnapshot(item.getProductTypeSnapshot());
            source.setActualQuantity(item.getRequiredQuantity());
            PurchaseDemandEntity demand = PurchaseSnapshotFactory.demand(source, order, batch.getSupplierId(),
                    batch.getWarehouseId(), ownerResolver.resolveForCreate(batch.getPurchaserId()));
            demand.setCalculationBatchId(batch.getId());
            demand.setCalculationBatchItemId(item.getId());
            if (purchaseDemandDao.insertIgnore(demand) == 1) {
                result.getDemandIds().add(demand.getId());
                result.setCreatedCount(result.getCreatedCount() + 1);
            } else {
                result.setSkippedCount(result.getSkippedCount() + 1);
            }
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("demandIds", result.getDemandIds());
        snapshot.put("createdCount", result.getCreatedCount());
        snapshot.put("skippedCount", result.getSkippedCount());
        if (batchDao.markGenerated(batch.getId(), result.getCreatedCount(), result.getSkippedCount(), snapshot,
                OffsetDateTime.now(), ScmOperator.current()) != 1) {
            throw new ScmBusinessException(PurchaseErrorCode.PURCHASE_DEMAND_ALLOCATION_CONFLICT);
        }
        idempotencyService.complete(claim, "PURCHASE_DEMAND_CALCULATION_BATCH", batch.getId(), result);
        return result;
    }

    /**
     * 回看一个冻结批次：批次头 + 冻结解释行 + 逐行建议量。
     *
     * <p>
     * <b>只读</b>：所有数字都取自冻结快照，不回表重算，因此回看结果与当初生成时逐字一致。 这是 ADM-05「建议量可以逐项解释」的落点 —— 没有这一步，批次一旦生成就只剩计数，解释链断了。
     */
    @Transactional(readOnly = true)
    public PurchaseDemandCalculationBatchDetailVO detail(Long batchId) {
        PurchaseDemandCalculationBatchDetailVO detail = batchDao.selectHeader(batchId);
        if (detail == null) {
            throw new ScmBusinessException(PurchaseErrorCode.PURCHASE_DEMAND_BATCH_NOT_FOUND);
        }
        requireVisible(detail);
        PurchaseDemandCalculationBatchEntity batch = batchDao.selectById(batchId);
        detail.setSummary(summaryRows(batch == null ? null : batch.getSummarySnapshot()));
        detail.setItems(batchItemDao.listByBatchId(batchId).stream()
                .map(PurchaseDemandCalculationBatchService::itemVo).toList());
        return detail;
    }

    /**
     * 批次可见性：仓库范围管解释行里的库存数字，采购员范围管这个采购归属的批次本身。
     *
     * <p>
     * 两个维度<b>相交而不互相替代</b>：只判仓库会让任何有查看权的人按 id 猜出别人的采购批次； 只判采购员则会把未授权仓库的库存量随解释行一起发出去。
     */
    private void requireVisible(PurchaseDemandCalculationBatchDetailVO detail) {
        if (!dataScopeService.resolve().getWarehouseScope().allows(detail.getWarehouseId())) {
            throw new ScmDataScopeException();
        }
        ownerResolver.requireVisible(detail.getPurchaserId());
    }

    private Map<Long, PurchaseDemandEntity> existing(List<SalesOrderItemEntity> sourceItems) {
        if (sourceItems.isEmpty()) return Map.of();
        return purchaseDemandDao
                .listActiveBySourceItemIds(sourceItems.stream().map(SalesOrderItemEntity::getId).toList()).stream()
                .collect(Collectors.toMap(PurchaseDemandEntity::getSalesOrderItemId, Function.identity()));
    }

    private static BigDecimal consume(Map<String, BigDecimal> gaps, String key, BigDecimal sourceQuantity) {
        BigDecimal remaining = gaps.getOrDefault(key, BigDecimal.ZERO);
        BigDecimal value = remaining.min(sourceQuantity).max(BigDecimal.ZERO).setScale(4);
        gaps.put(key, remaining.subtract(value));
        return value;
    }

    private static String key(Long skuId, String unit) {
        return skuId + "|" + unit;
    }

    private static void validateWindow(OffsetDateTime startAt, OffsetDateTime endAt) {
        if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {
            throw new ScmBusinessException(PurchaseErrorCode.PURCHASE_QUANTITY_INVALID);
        }
    }

    private static PurchaseDemandCalculationBatchVO toVO(PurchaseDemandCalculationBatchEntity batch) {
        PurchaseDemandCalculationBatchVO vo = new PurchaseDemandCalculationBatchVO();
        vo.setBatchId(batch.getId());
        vo.setStatus(batch.getStatus());
        vo.setSourceLineCount(batch.getSourceLineCount());
        vo.setCandidateLineCount(batch.getCandidateLineCount());
        vo.setGeneratedCount(batch.getGeneratedCount());
        vo.setSkippedCount(batch.getSkippedCount());
        vo.setSummary(summaryRows(batch.getSummarySnapshot()));
        return vo;
    }

    /**
     * 冻结快照里的解释行（快照是 {@code {"rows":[...]}}）。
     *
     * <p>
     * 结构不符或字段缺失时退化成空列表而不是抛异常：批次头本身仍然可读， 让一个损坏的快照把整次回看打成 500 是拿不到任何信息的。
     */
    private static List<Map<String, Object>> summaryRows(Map<String, Object> snapshot) {
        if (snapshot == null || !(snapshot.get("rows") instanceof List<?> rows)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Object row : rows) {
            if (row instanceof Map<?, ?> map) {
                Map<String, Object> copy = new LinkedHashMap<>();
                map.forEach((key, value) -> copy.put(String.valueOf(key), value));
                result.add(copy);
            }
        }
        return result;
    }

    private static PurchaseDemandCalculationBatchItemVO itemVo(PurchaseDemandCalculationBatchItemEntity row) {
        PurchaseDemandCalculationBatchItemVO vo = new PurchaseDemandCalculationBatchItemVO();
        vo.setLineNo(row.getLineNo());
        vo.setSalesOrderNo(row.getSalesOrderNoSnapshot());
        vo.setSourceConfirmedAt(row.getSourceConfirmedAt());
        vo.setSkuId(row.getSkuId());
        vo.setSkuCode(row.getSkuCodeSnapshot());
        vo.setProductName(row.getProductNameSnapshot());
        vo.setSkuName(row.getSkuNameSnapshot());
        vo.setSpecValues(row.getSpecValuesSnapshot());
        vo.setDemandUnit(row.getDemandUnitSnapshot());
        vo.setProductType(row.getProductTypeSnapshot());
        vo.setSourceQuantity(row.getSourceQuantity());
        vo.setRequiredQuantity(row.getRequiredQuantity());
        vo.setExistingDemandId(row.getExistingDemandId());
        return vo;
    }
}
