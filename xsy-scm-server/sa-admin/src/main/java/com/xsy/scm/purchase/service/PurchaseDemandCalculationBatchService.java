package com.xsy.scm.purchase.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
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

        PurchaseDemandCalculationBatchEntity batch = new PurchaseDemandCalculationBatchEntity();
        batch.setStartAt(form.getStartAt());
        batch.setEndAt(form.getEndAt());
        batch.setWarehouseId(form.getWarehouseId());
        batch.setSupplierId(form.getSupplierId());
        batch.setPurchaserId(form.getPurchaserId());
        batch.setCategoryId(form.getCategoryId());
        batch.setKeyword(form.getKeyword());
        batch.setStatus("READY");
        batch.setSourceLineCount(sourceItems.size());
        batch.setCreatedAt(OffsetDateTime.now());
        batch.setCreatedBy(ScmOperator.current());
        batch.setSummarySnapshot(summary.stream().map(row -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("skuId", row.getSkuId());
            value.put("demandUnit", row.getDemandUnit());
            value.put("orderDemandQuantity", PurchaseSnapshotFactory.fixed(row.getOrderDemandQuantity()));
            value.put("netPurchaseGap", PurchaseSnapshotFactory.fixed(row.getNetPurchaseGap()));
            value.put("inTransitQuantity", PurchaseSnapshotFactory.fixed(row.getInTransitQuantity()));
            value.put("purchaseCoverageQuantity", PurchaseSnapshotFactory.fixed(row.getPurchaseCoverageQuantity()));
            return value;
        }).toList());

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
        batch.setCandidateLineCount((int) items.stream().filter(item -> item.getRequiredQuantity().signum() > 0).count());
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

    private Map<Long, PurchaseDemandEntity> existing(List<SalesOrderItemEntity> sourceItems) {
        if (sourceItems.isEmpty()) return Map.of();
        return purchaseDemandDao.listActiveBySourceItemIds(sourceItems.stream().map(SalesOrderItemEntity::getId).toList())
                .stream().collect(Collectors.toMap(PurchaseDemandEntity::getSalesOrderItemId, Function.identity()));
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
        vo.setSummary(batch.getSummarySnapshot() == null ? List.of() : List.of(batch.getSummarySnapshot()));
        return vo;
    }
}
