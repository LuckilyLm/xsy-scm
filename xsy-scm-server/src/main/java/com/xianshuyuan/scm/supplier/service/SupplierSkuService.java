package com.xianshuyuan.scm.supplier.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.common.exception.ErrorCode;
import com.xianshuyuan.scm.customer.entity.EnabledStatus;
import com.xianshuyuan.scm.product.entity.ProductSkuEntity;
import com.xianshuyuan.scm.product.mapper.ProductSkuMapper;
import com.xianshuyuan.scm.supplier.converter.SupplierConverter;
import com.xianshuyuan.scm.supplier.dto.SupplierSkuSaveRequest;
import com.xianshuyuan.scm.supplier.entity.SupplierSkuEntity;
import com.xianshuyuan.scm.supplier.mapper.SupplierSkuMapper;
import com.xianshuyuan.scm.supplier.vo.SupplierSkuVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SupplierSkuService {
    private final SupplierService masterData;
    private final SupplierSkuMapper configurations;
    private final ProductSkuMapper skus;

    public SupplierSkuService(SupplierService masterData, SupplierSkuMapper configurations, ProductSkuMapper skus) {
        this.masterData = masterData;
        this.configurations = configurations;
        this.skus = skus;
    }

    public List<SupplierSkuVO> listForSupplier(long supplierId) {
        masterData.requireSupplier(supplierId);
        return configurations.selectActiveBySupplierId(supplierId).stream().map(SupplierConverter::toVO).toList();
    }

    public List<SupplierSkuEntity> listEnabledForPurchasing(long skuId) {
        return configurations.selectEnabledBySkuId(skuId);
    }

    /**
     * Resolves the active supplier SKU configuration for a purchase command.
     * The parent supplier is checked separately so stale/disabled master data
     * cannot be bypassed by a configuration query.
     */
    public SupplierSkuEntity requireEnabledForPurchasing(long supplierId, long skuId) {
        masterData.requireEnabledSupplier(supplierId);
        return configurations.selectEnabledBySkuId(skuId).stream()
                .filter(row -> Objects.equals(row.getSupplierId(), supplierId)
                        && Objects.equals(row.getSkuId(), skuId)
                        && row.getStatus() == EnabledStatus.ENABLED
                        && !Boolean.TRUE.equals(row.getDeleted()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(SupplierErrorCodes.SUPPLIER_SKU_NOT_FOUND));
    }

    @Transactional
    public void replaceForSupplier(long supplierId, List<SupplierSkuSaveRequest> requests) {
        masterData.requireEnabledSupplierForUpdate(supplierId);
        var existing = configurations.selectActiveBySupplierIdForUpdate(supplierId);
        var existingById = existing.stream().collect(Collectors.toMap(SupplierSkuEntity::getId, Function.identity()));
        var existingBySkuId = existing.stream().collect(Collectors.toMap(SupplierSkuEntity::getSkuId, Function.identity()));
        var seenSkuIds = new HashSet<Long>();
        for (var request : requests) {
            if (!Objects.equals(request.supplierId(), supplierId) || !seenSkuIds.add(request.skuId())) {
                throw new BusinessException(SupplierErrorCodes.SUPPLIER_SKU_DUPLICATE);
            }
        }

        var retained = new HashSet<Long>();
        var replacements = new ArrayList<Replacement>();
        for (var request : requests) {
            SupplierSkuEntity matched;
            if (request.id() != null) {
                matched = existingById.get(request.id());
                if (matched == null || !Objects.equals(matched.getSkuId(), request.skuId())) {
                    throw new BusinessException(SupplierErrorCodes.SUPPLIER_SKU_DUPLICATE);
                }
            } else {
                matched = existingBySkuId.get(request.skuId());
            }
            if (matched != null) {
                if (request.version() == null || !Objects.equals(matched.getVersion(), request.version())) {
                    throw new BusinessException(SupplierErrorCodes.VERSION_CONFLICT);
                }
                retained.add(matched.getId());
            }
            replacements.add(new Replacement(matched, request, build(request)));
        }
        for (var replacement : replacements) {
            if (replacement.existing() == null) {
                create(replacement.entity());
            } else {
                updateExisting(replacement.existing(), replacement.request(), replacement.entity());
            }
        }
        for (var omitted : existing) {
            if (!retained.contains(omitted.getId())
                    && configurations.softDeleteOwnedWithVersion(supplierId, omitted.getId(), omitted.getVersion()) != 1) {
                throw new BusinessException(SupplierErrorCodes.VERSION_CONFLICT);
            }
        }
    }

    private record Replacement(
            SupplierSkuEntity existing,
            SupplierSkuSaveRequest request,
            SupplierSkuEntity entity
    ) {
    }

    private boolean supplierIdEquals(SupplierSkuEntity entity, long supplierId) {
        return entity.getSupplierId() != null && entity.getSupplierId() == supplierId;
    }

    @Transactional
    public long create(SupplierSkuSaveRequest request) {
        return create(build(request));
    }

    private long create(SupplierSkuEntity entity) {
        entity.setVersion(0);
        entity.setDeleted(false);
        entity.setCreatedBy("SYSTEM");
        configurations.insert(entity);
        return entity.getId();
    }

    @Transactional
    public void update(long id, SupplierSkuSaveRequest request) {
        updateExisting(require(id), request);
    }

    private void updateExisting(SupplierSkuEntity existing, SupplierSkuSaveRequest request) {
        updateExisting(existing, request, build(request));
    }

    private void updateExisting(
            SupplierSkuEntity existing,
            SupplierSkuSaveRequest request,
            SupplierSkuEntity entity
    ) {
        long id = existing.getId();
        if (request.version() == null) throw new BusinessException(SupplierErrorCodes.VERSION_CONFLICT);
        if (existing.getSupplierId() != null && !supplierIdEquals(existing, request.supplierId())) {
            throw new BusinessException(SupplierErrorCodes.SUPPLIER_SKU_DUPLICATE);
        }
        if (existing.getSkuId() != null && !existing.getSkuId().equals(request.skuId())) {
            throw new BusinessException(SupplierErrorCodes.SUPPLIER_SKU_DUPLICATE);
        }
        entity.setId(id);
        entity.setVersion(request.version());
        entity.setUpdatedBy("SYSTEM");
        if (configurations.updateById(entity) != 1) throw new BusinessException(SupplierErrorCodes.VERSION_CONFLICT);
    }

    private SupplierSkuEntity require(long id) {
        var entity = configurations.selectById(id);
        if (entity == null || Boolean.TRUE.equals(entity.getDeleted())) {
            throw new BusinessException(SupplierErrorCodes.SUPPLIER_SKU_NOT_FOUND);
        }
        return entity;
    }

    private SupplierSkuEntity build(SupplierSkuSaveRequest request) {
        var supplier = masterData.requireEnabledSupplier(request.supplierId());
        ProductSkuEntity sku = skus.selectOrderableByIds(List.of(request.skuId()))
                .stream()
                .filter(candidate -> Objects.equals(candidate.getId(), request.skuId()))
                .findFirst()
                .orElse(null);
        if (sku == null) {
            throw new BusinessException(SupplierErrorCodes.SKU_DISABLED);
        }
        var entity = new SupplierSkuEntity();
        entity.setSupplierId(supplier.getId());
        entity.setSkuId(sku.getId());
        entity.setSupplierCodeSnapshot(supplier.getSupplierCode());
        entity.setSupplierNameSnapshot(supplier.getName());
        entity.setSkuCodeSnapshot(sku.getSkuCode());
        entity.setSkuNameSnapshot(sku.getProductName());
        Map<String, String> values = sku.getSpecValues();
        entity.setSpecValuesSnapshot(values == null ? Map.of() : Map.copyOf(values));
        entity.setPurchaseUnit(request.purchaseUnit());
        entity.setReferencePrice(parseReferencePrice(request.referencePrice()));
        entity.setPurchaserId(request.purchaserId());
        entity.setDefaultSupplier(request.defaultSupplier());
        entity.setStatus(request.status() == null ? EnabledStatus.ENABLED : request.status());
        return entity;
    }

    private BigDecimal parseReferencePrice(String value) {
        if (value == null) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "参考价格式不正确");
        }
    }
}
