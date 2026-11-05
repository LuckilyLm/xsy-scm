package com.xsy.scm.purchase.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.purchase.dao.PurchaseDemandCalculationBatchDao;
import com.xsy.scm.purchase.dao.PurchaseDemandCalculationBatchItemDao;
import com.xsy.scm.purchase.dao.PurchaseDemandDao;
import com.xsy.scm.purchase.dao.PurchaseInquiryDao;
import com.xsy.scm.purchase.domain.entity.PurchaseDemandCalculationBatchEntity;
import com.xsy.scm.purchase.domain.entity.PurchaseDemandCalculationBatchItemEntity;
import com.xsy.scm.purchase.domain.form.PurchaseInquiryForm;
import com.xsy.scm.purchase.domain.vo.PurchaseInquiryVO;
import com.xsy.scm.purchase.domain.vo.PurchaseInquiryVO.Inquiry;
import com.xsy.scm.purchase.domain.vo.PurchaseInquiryVO.Quote;
import com.xsy.scm.purchase.support.PurchaseOwnerResolver;
import com.xsy.scm.purchase.support.PurchaseInquiryDecisionPolicy;
import com.xsy.scm.supplier.service.SupplierService;
import com.xsy.scm.supplier.service.SupplierSkuService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;
import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_DEMAND_BATCH_NOT_FOUND;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_INQUIRY_NOT_FOUND;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_INQUIRY_QUOTE_INVALID;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_INQUIRY_STATE_INVALID;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_UNIT_CONVERSION_REQUIRED;

/**
 * 询价是在已固定的净需求之上做采购决策，不自动写采购单、采购需求或库存。
 */
@Service
@RequiredArgsConstructor
public class PurchaseInquiryService {
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("99999999999999.9999");
    private static final int PAGE_SIZE = 20;
    private static final int HISTORY_SIZE = 30;
    private final PurchaseInquiryDao inquiryDao;
    private final PurchaseDemandCalculationBatchDao batchDao;
    private final PurchaseDemandCalculationBatchItemDao batchItemDao;
    private final PurchaseDemandDao purchaseDemandDao;
    private final PurchaseOwnerResolver ownerResolver;
    private final ScmDataScopeService scopeService;
    private final SupplierSkuService supplierSkuService;
    private final SupplierService supplierService;
    private final PurchaseIdempotencyService idempotencyService;

    @Transactional(readOnly = true)
    public PurchaseInquiryVO.BatchPage byBatch(Long batchId, Integer page) {
        requireBatch(batchId);
        int current = page == null ? 1 : page;
        if (current < 1 || (long) (current - 1) * PAGE_SIZE > Integer.MAX_VALUE) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        return new PurchaseInquiryVO.BatchPage(inquiryDao.pageByBatch(batchId, PAGE_SIZE, (current - 1) * PAGE_SIZE),
            inquiryDao.countByBatch(batchId));
    }

    @Transactional(readOnly = true)
    public List<PurchaseInquiryVO.Candidate> candidates(Long batchId) {
        var batch = requireBatch(batchId);
        if (!"READY".equals(batch.getStatus()))
            return List.of();
        var eligible = eligibleLines(batchId);
        Set<Long> alreadyInDemand = activeDemandSourceIds(eligible);
        Map<String, PurchaseInquiryVO.Candidate> grouped = new LinkedHashMap<>();
        Set<String> blocked = eligible.stream().filter(item -> alreadyInDemand.contains(item.getSalesOrderItemId()))
            .map(item -> item.getSkuId() + "|" + item.getDemandUnitSnapshot()).collect(Collectors.toSet());
        for (var item : eligible) {
            String grouping = item.getSkuId() + "|" + item.getDemandUnitSnapshot();
            if (blocked.contains(grouping))
                continue;
            var row = grouped.get(grouping);
            if (row == null) {
                row = new PurchaseInquiryVO.Candidate();
                row.setSkuId(item.getSkuId());
                row.setSkuCode(item.getSkuCodeSnapshot());
                row.setProductName(item.getProductNameSnapshot());
                row.setDemandUnit(item.getDemandUnitSnapshot());
                row.setRequiredQuantity(BigDecimal.ZERO);
                grouped.put(grouping, row);
            }
            row.setRequiredQuantity(row.getRequiredQuantity().add(item.getRequiredQuantity()));
        }
        var existing = inquiryDao.byBatch(batchId).stream().collect(
            Collectors.toMap(row -> row.getSkuId() + "|" + row.getDemandUnit(), Inquiry::getId, (left, right) -> left));
        grouped.forEach((sourceKey, row) -> row.setExistingInquiryId(existing.get(sourceKey)));
        return List.copyOf(grouped.values());
    }

    @Transactional(readOnly = true)
    public PurchaseInquiryVO.Detail detail(Long id) {
        Inquiry inquiry = requireVisible(id);
        var history = historyPage(id, null);
        return new PurchaseInquiryVO.Detail(inquiry, inquiryDao.currentOffers(id), history.records(), history.hasMore(),
            history.nextBeforeId());
    }

    @Transactional(readOnly = true)
    public PurchaseInquiryVO.HistoryPage history(Long inquiryId, Long beforeId) {
        requireVisible(inquiryId);
        if (beforeId != null && beforeId <= 0)
            throw new ScmBusinessException(VALIDATION_ERROR);
        return historyPage(inquiryId, beforeId);
    }

    private PurchaseInquiryVO.HistoryPage historyPage(Long inquiryId, Long beforeId) {
        var rows = inquiryDao.history(inquiryId, beforeId, HISTORY_SIZE + 1);
        boolean hasMore = rows.size() > HISTORY_SIZE;
        List<Quote> records = hasMore ? rows.subList(0, HISTORY_SIZE) : rows;
        Long next = hasMore ? records.getLast().getId() : null;
        return new PurchaseInquiryVO.HistoryPage(List.copyOf(records), hasMore, next);
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseInquiryVO.Detail create(PurchaseInquiryForm.Create form, String key) {
        requireBatch(form.getBatchId());
        var claim = idempotencyService.claim("PURCHASE_INQUIRY_CREATE", key, form);
        if (claim.replay())
            return idempotencyService.replay(claim, PurchaseInquiryVO.Detail.class);
        PurchaseDemandCalculationBatchEntity batch = batchDao.lock(form.getBatchId());
        if (batch == null)
            throw new ScmBusinessException(PURCHASE_DEMAND_BATCH_NOT_FOUND);
        requireScope(batch.getWarehouseId(), batch.getPurchaserId());
        if (!"READY".equals(batch.getStatus())) {
            throw new ScmBusinessException(PURCHASE_INQUIRY_STATE_INVALID);
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (form.getDeadlineAt() == null || !form.getDeadlineAt().isAfter(now) || form.getDemandUnit() == null
            || form.getDemandUnit().isBlank()) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }

        List<PurchaseDemandCalculationBatchItemEntity> lines = eligibleLines(batch.getId()).stream()
            .filter(item -> Objects.equals(item.getSkuId(), form.getSkuId()))
            .filter(item -> Objects.equals(item.getDemandUnitSnapshot(), form.getDemandUnit())).toList();
        if (lines.isEmpty())
            throw new ScmBusinessException(PURCHASE_INQUIRY_QUOTE_INVALID);
        if (!activeDemandSourceIds(lines).isEmpty()) {
            throw new ScmBusinessException(PURCHASE_INQUIRY_STATE_INVALID);
        }
        BigDecimal required = lines.stream().map(PurchaseDemandCalculationBatchItemEntity::getRequiredQuantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (required.compareTo(MAX_QUANTITY) > 0)
            throw new ScmBusinessException(VALIDATION_ERROR);
        var snapshot = lines.getFirst();
        Inquiry inquiry = new Inquiry();
        inquiry.setBatchId(batch.getId());
        inquiry.setWarehouseId(batch.getWarehouseId());
        inquiry.setPurchaserId(batch.getPurchaserId());
        inquiry.setSkuId(form.getSkuId());
        inquiry.setSkuCodeSnapshot(snapshot.getSkuCodeSnapshot());
        inquiry.setProductNameSnapshot(snapshot.getProductNameSnapshot());
        inquiry.setDemandUnit(form.getDemandUnit());
        inquiry.setRequiredQuantity(required);
        inquiry.setDeadlineAt(form.getDeadlineAt());
        inquiry.setCreatedBy(ScmOperator.current());
        try {
            inquiryDao.create(inquiry);
        } catch (DuplicateKeyException duplicateInquiry) {
            throw new ScmBusinessException(PURCHASE_INQUIRY_STATE_INVALID);
        }
        PurchaseInquiryVO.Detail result = detail(inquiry.getId());
        idempotencyService.complete(claim, "PURCHASE_INQUIRY", inquiry.getId(), result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseInquiryVO.Detail quote(PurchaseInquiryForm.Quote form, String key) {
        Inquiry inquiry = requireLocked(form.getInquiryId());
        var claim = idempotencyService.claim("PURCHASE_INQUIRY_QUOTE:" + inquiry.getId(), key, form);
        if (claim.replay())
            return idempotencyService.replay(claim, PurchaseInquiryVO.Detail.class);
        PurchaseInquiryDecisionPolicy.requireQuoteWritable(inquiry, form.getVersion(), OffsetDateTime.now());
        BigDecimal price = price(form.getUnitPrice());
        if (!PurchaseInquiryDecisionPolicy.isQuoteStillValid(form.getValidUntil(), OffsetDateTime.now())) {
            throw new ScmBusinessException(PURCHASE_INQUIRY_QUOTE_INVALID);
        }
        var relation = supplierSkuService.requireEnabledForPurchasing(form.getSupplierId(), inquiry.getSkuId());
        if (!Objects.equals(relation.getPurchaseUnit(), inquiry.getDemandUnit())) {
            throw new ScmBusinessException(PURCHASE_UNIT_CONVERSION_REQUIRED);
        }
        var supplier = supplierService.requireEnabled(form.getSupplierId());
        Quote quote = new Quote();
        quote.setInquiryId(inquiry.getId());
        quote.setSupplierId(form.getSupplierId());
        quote.setSupplierNameSnapshot(supplier.getName());
        quote.setRevision(inquiryDao.maxRevision(inquiry.getId(), form.getSupplierId()) + 1);
        quote.setPurchaseUnit(relation.getPurchaseUnit());
        quote.setUnitPrice(price);
        quote.setValidUntil(form.getValidUntil());
        quote.setRemark(form.getRemark());
        quote.setRecordedBy(ScmOperator.current());
        if (inquiryDao.bump(inquiry.getId(), inquiry.getVersion()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        inquiryDao.quote(quote);
        PurchaseInquiryVO.Detail result = detail(inquiry.getId());
        idempotencyService.complete(claim, "PURCHASE_INQUIRY_QUOTE", quote.getId(), result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseInquiryVO.Detail accept(PurchaseInquiryForm.Accept form, String key) {
        Inquiry inquiry = requireLocked(form.getInquiryId());
        var claim = idempotencyService.claim("PURCHASE_INQUIRY_ACCEPT:" + inquiry.getId(), key, form);
        if (claim.replay())
            return idempotencyService.replay(claim, PurchaseInquiryVO.Detail.class);
        PurchaseInquiryDecisionPolicy.requireAwaitingDecision(inquiry, form.getVersion());
        Quote selected = inquiryDao.quoteById(form.getQuoteId(), inquiry.getId());
        if (selected == null
            || !PurchaseInquiryDecisionPolicy.isQuoteStillValid(selected.getValidUntil(), OffsetDateTime.now())) {
            throw new ScmBusinessException(PURCHASE_INQUIRY_QUOTE_INVALID);
        }
        Quote latest = inquiryDao.latestSupplierQuote(inquiry.getId(), selected.getSupplierId());
        if (!Objects.equals(latest.getId(), selected.getId())) {
            throw new ScmBusinessException(PURCHASE_INQUIRY_QUOTE_INVALID);
        }
        var currentSupplierSku = supplierSkuService.requireEnabledForPurchasing(selected.getSupplierId(),
            inquiry.getSkuId());
        if (!Objects.equals(currentSupplierSku.getPurchaseUnit(), selected.getPurchaseUnit())
            || !Objects.equals(selected.getPurchaseUnit(), inquiry.getDemandUnit())) {
            throw new ScmBusinessException(PURCHASE_UNIT_CONVERSION_REQUIRED);
        }
        if (inquiryDao.accept(inquiry.getId(), inquiry.getVersion(), selected.getId(), ScmOperator.current()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        PurchaseInquiryVO.Detail result = detail(inquiry.getId());
        idempotencyService.complete(claim, "PURCHASE_INQUIRY", inquiry.getId(), result);
        return result;
    }

    private Inquiry requireVisible(Long id) {
        Inquiry inquiry = inquiryDao.find(id);
        if (inquiry == null)
            throw new ScmBusinessException(PURCHASE_INQUIRY_NOT_FOUND);
        requireScope(inquiry.getWarehouseId(), inquiry.getPurchaserId());
        return inquiry;
    }

    private Inquiry requireLocked(Long id) {
        Inquiry inquiry = inquiryDao.lock(id);
        if (inquiry == null)
            throw new ScmBusinessException(PURCHASE_INQUIRY_NOT_FOUND);
        requireScope(inquiry.getWarehouseId(), inquiry.getPurchaserId());
        return inquiry;
    }

    private PurchaseDemandCalculationBatchEntity requireBatch(Long id) {
        PurchaseDemandCalculationBatchEntity batch = batchDao.selectById(id);
        if (batch == null)
            throw new ScmBusinessException(PURCHASE_DEMAND_BATCH_NOT_FOUND);
        requireScope(batch.getWarehouseId(), batch.getPurchaserId());
        return batch;
    }

    private void requireScope(Long warehouseId, Long purchaserId) {
        if (!scopeService.resolve().getWarehouseScope().allows(warehouseId)) {
            throw new ScmDataScopeException();
        }
        ownerResolver.requireVisible(purchaserId);
    }

    private List<PurchaseDemandCalculationBatchItemEntity> eligibleLines(Long batchId) {
        return batchItemDao.listByBatchId(batchId).stream().filter(row -> row.getExistingDemandId() == null
            && row.getRequiredQuantity() != null && row.getRequiredQuantity().signum() > 0).toList();
    }

    private Set<Long> activeDemandSourceIds(List<PurchaseDemandCalculationBatchItemEntity> lines) {
        if (lines.isEmpty())
            return Set.of();
        return purchaseDemandDao
            .listActiveBySourceItemIds(
                lines.stream().map(PurchaseDemandCalculationBatchItemEntity::getSalesOrderItemId).distinct().toList())
            .stream().map(row -> row.getSalesOrderItemId()).collect(Collectors.toSet());
    }

    private static BigDecimal price(String input) {
        if (input == null || !input.matches("\\d{1,14}(?:\\.\\d{1,4})?")) {
            throw new ScmBusinessException(PURCHASE_INQUIRY_QUOTE_INVALID);
        }
        return new BigDecimal(input);
    }
}
