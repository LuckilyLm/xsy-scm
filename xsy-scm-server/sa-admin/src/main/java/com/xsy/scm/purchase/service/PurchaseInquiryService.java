package com.xsy.scm.purchase.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.purchase.dao.PurchaseDemandCalculationBatchDao;
import com.xsy.scm.purchase.dao.PurchaseDemandCalculationBatchItemDao;
import com.xsy.scm.purchase.dao.PurchaseInquiryDao;
import com.xsy.scm.purchase.domain.entity.PurchaseDemandCalculationBatchEntity;
import com.xsy.scm.purchase.domain.entity.PurchaseDemandCalculationBatchItemEntity;
import com.xsy.scm.purchase.domain.form.PurchaseInquiryForm;
import com.xsy.scm.purchase.domain.vo.PurchaseInquiryVO;
import com.xsy.scm.purchase.domain.vo.PurchaseInquiryVO.Inquiry;
import com.xsy.scm.purchase.domain.vo.PurchaseInquiryVO.Quote;
import com.xsy.scm.purchase.support.PurchaseOwnerResolver;
import com.xsy.scm.supplier.service.SupplierService;
import com.xsy.scm.supplier.service.SupplierSkuService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
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

/** 询价是冻结净需求之上的采购决策记录，不自动写采购单、采购需求或库存。 */
@Service
@RequiredArgsConstructor
public class PurchaseInquiryService {
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("99999999999999.9999");
    private final PurchaseInquiryDao inquiryDao;
    private final PurchaseDemandCalculationBatchDao batchDao;
    private final PurchaseDemandCalculationBatchItemDao batchItemDao;
    private final PurchaseOwnerResolver ownerResolver;
    private final ScmDataScopeService scopeService;
    private final SupplierSkuService supplierSkuService;
    private final SupplierService supplierService;
    private final PurchaseIdempotencyService idempotencyService;

    @Transactional(readOnly = true)
    public List<Inquiry> byBatch(Long batchId) {
        requireBatch(batchId);
        return inquiryDao.byBatch(batchId);
    }

    @Transactional(readOnly = true)
    public List<PurchaseInquiryVO.Candidate> candidates(Long batchId) {
        requireBatch(batchId);
        Map<String, PurchaseInquiryVO.Candidate> grouped = new LinkedHashMap<>();
        for (var item : batchItemDao.listByBatchId(batchId)) {
            if (item.getExistingDemandId() != null || item.getRequiredQuantity() == null
                    || item.getRequiredQuantity().signum() <= 0) continue;
            String grouping = item.getSkuId() + "|" + item.getDemandUnitSnapshot();
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
        return List.copyOf(grouped.values());
    }

    @Transactional(readOnly = true)
    public PurchaseInquiryVO.Detail detail(Long id) {
        Inquiry inquiry = requireVisible(id);
        return new PurchaseInquiryVO.Detail(inquiry, inquiryDao.currentOffers(id), inquiryDao.quotes(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseInquiryVO.Detail create(PurchaseInquiryForm.Create form, String key) {
        PurchaseDemandCalculationBatchEntity batch = requireBatch(form.getBatchId());
        var claim = idempotencyService.claim("PURCHASE_INQUIRY_CREATE", key, form);
        if (claim.replay()) return idempotencyService.replay(claim, PurchaseInquiryVO.Detail.class);
        OffsetDateTime now = OffsetDateTime.now();
        if (form.getDeadlineAt() == null || !form.getDeadlineAt().isAfter(now)
                || form.getDemandUnit() == null || form.getDemandUnit().isBlank()) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }

        List<PurchaseDemandCalculationBatchItemEntity> lines = batchItemDao.listByBatchId(batch.getId()).stream()
                .filter(item -> Objects.equals(item.getSkuId(), form.getSkuId()))
                .filter(item -> Objects.equals(item.getDemandUnitSnapshot(), form.getDemandUnit()))
                .filter(item -> item.getExistingDemandId() == null)
                .filter(item -> item.getRequiredQuantity() != null && item.getRequiredQuantity().signum() > 0)
                .toList();
        if (lines.isEmpty()) throw new ScmBusinessException(PURCHASE_INQUIRY_QUOTE_INVALID);
        BigDecimal required = lines.stream().map(PurchaseDemandCalculationBatchItemEntity::getRequiredQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (required.compareTo(MAX_QUANTITY) > 0) throw new ScmBusinessException(VALIDATION_ERROR);
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
        if (claim.replay()) return idempotencyService.replay(claim, PurchaseInquiryVO.Detail.class);
        requireOpen(inquiry, form.getVersion());
        BigDecimal price = price(form.getUnitPrice());
        if (form.getValidUntil() == null || !form.getValidUntil().isAfter(OffsetDateTime.now())) {
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
        if (claim.replay()) return idempotencyService.replay(claim, PurchaseInquiryVO.Detail.class);
        requireOpen(inquiry, form.getVersion());
        Quote selected = inquiryDao.quoteById(form.getQuoteId(), inquiry.getId());
        if (selected == null || !selected.getValidUntil().isAfter(OffsetDateTime.now())) {
            throw new ScmBusinessException(PURCHASE_INQUIRY_QUOTE_INVALID);
        }
        Quote latest = inquiryDao.latestSupplierQuote(inquiry.getId(), selected.getSupplierId());
        if (!Objects.equals(latest.getId(), selected.getId())) {
            throw new ScmBusinessException(PURCHASE_INQUIRY_QUOTE_INVALID);
        }
        var currentSupplierSku = supplierSkuService.requireEnabledForPurchasing(
                selected.getSupplierId(), inquiry.getSkuId());
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
        if (inquiry == null) throw new ScmBusinessException(PURCHASE_INQUIRY_NOT_FOUND);
        requireScope(inquiry.getWarehouseId(), inquiry.getPurchaserId());
        return inquiry;
    }

    private Inquiry requireLocked(Long id) {
        Inquiry inquiry = inquiryDao.lock(id);
        if (inquiry == null) throw new ScmBusinessException(PURCHASE_INQUIRY_NOT_FOUND);
        requireScope(inquiry.getWarehouseId(), inquiry.getPurchaserId());
        return inquiry;
    }

    private PurchaseDemandCalculationBatchEntity requireBatch(Long id) {
        PurchaseDemandCalculationBatchEntity batch = batchDao.selectById(id);
        if (batch == null) throw new ScmBusinessException(PURCHASE_DEMAND_BATCH_NOT_FOUND);
        requireScope(batch.getWarehouseId(), batch.getPurchaserId());
        return batch;
    }

    private void requireScope(Long warehouseId, Long purchaserId) {
        if (!scopeService.resolve().getWarehouseScope().allows(warehouseId)) {
            throw new ScmDataScopeException();
        }
        ownerResolver.requireVisible(purchaserId);
    }

    private static void requireOpen(Inquiry inquiry, Integer expectedVersion) {
        if (!Objects.equals(inquiry.getVersion(), expectedVersion)) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        if (!"OPEN".equals(inquiry.getStatus()) || !inquiry.getDeadlineAt().isAfter(OffsetDateTime.now())) {
            throw new ScmBusinessException(PURCHASE_INQUIRY_STATE_INVALID);
        }
    }

    private static BigDecimal price(String input) {
        if (input == null || !input.matches("\\d{1,14}(?:\\.\\d{1,4})?")) {
            throw new ScmBusinessException(PURCHASE_INQUIRY_QUOTE_INVALID);
        }
        return new BigDecimal(input);
    }
}
