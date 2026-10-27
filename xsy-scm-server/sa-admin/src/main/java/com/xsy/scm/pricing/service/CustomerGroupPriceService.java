package com.xsy.scm.pricing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.util.ScmDecimalStrings;
import com.xsy.scm.customer.service.CustomerGroupService;
import com.xsy.scm.pricing.constant.ScmPriceOperationTypeEnum;
import com.xsy.scm.pricing.dao.CustomerGroupPriceDao;
import com.xsy.scm.pricing.domain.entity.CustomerGroupPriceEntity;
import com.xsy.scm.pricing.domain.form.CustomerGroupPriceAddForm;
import com.xsy.scm.pricing.domain.form.CustomerGroupPriceDeleteForm;
import com.xsy.scm.pricing.domain.form.CustomerGroupPriceUpdateForm;
import com.xsy.scm.pricing.manager.PriceValidation;
import com.xsy.scm.product.dao.ProductSkuOptionDao;
import com.xsy.scm.product.domain.vo.ProductSkuOptionVO;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static com.xsy.scm.pricing.constant.PricingErrorCode.GROUP_PRICE_INVALID;
import static com.xsy.scm.pricing.constant.PricingErrorCode.GROUP_PRICE_NOT_FOUND;
import static com.xsy.scm.pricing.constant.PricingErrorCode.GROUP_PRICE_OVERLAP;
import static com.xsy.scm.pricing.constant.PricingErrorCode.SKU_NOT_SELLABLE;

/**
 * 客户分组价维护。
 *
 * <p>
 * 价格落在「分组 + 商品 + 销售单位」上，录入时选一个规格只是为了确定商品与单位：同一商品下同单位的规格 自动共享这条价格。跨单位（斤/箱/件）必须单独维护，不做隐式换算。
 */
@Service
@RequiredArgsConstructor
public class CustomerGroupPriceService {

    private final CustomerGroupPriceDao customerGroupPriceDao;

    private final CustomerGroupService customerGroupService;

    private final ProductSkuOptionDao productSkuOptionDao;

    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public Long add(CustomerGroupPriceAddForm form) {
        customerGroupService.requireSelectable(form.getGroupId());
        var sku = requireSku(form.getSkuId());
        lockGroup(form.getGroupId());
        PriceValidation.amountAndPeriod(form.getUnitPrice(), form.getEffectiveFrom(), form.getEffectiveTo());
        requireNoOverlap(form.getGroupId(), sku.getSpuId(), sku.getSaleUnit(), form.getEffectiveFrom(),
            form.getEffectiveTo(), null);
        var entity = new CustomerGroupPriceEntity();
        apply(entity, form, sku);
        customerGroupPriceDao.insert(entity);
        log(entity, ScmPriceOperationTypeEnum.CREATE, null);
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(CustomerGroupPriceUpdateForm form) {
        var existing = require(form.getGroupPriceId(), form.getVersion());
        var sku = requireSku(form.getSkuId());
        PriceValidation.amountAndPeriod(form.getUnitPrice(), form.getEffectiveFrom(), form.getEffectiveTo());
        // 维度变更时按分组 ID 升序锁住新旧分组，避免两条分组价互相穿插造成重叠。
        Stream.of(existing.getGroupId(), form.getGroupId()).distinct().sorted().forEach(this::lockGroup);
        customerGroupService.requireSelectable(form.getGroupId());
        requireNoOverlap(form.getGroupId(), sku.getSpuId(), sku.getSaleUnit(), form.getEffectiveFrom(),
            form.getEffectiveTo(), existing.getId());
        String beforeSnapshot = snapshot(existing);
        apply(existing, form, sku);
        existing.setVersion(form.getVersion());
        if (customerGroupPriceDao.updateById(existing) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        log(existing, ScmPriceOperationTypeEnum.UPDATE, beforeSnapshot);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(CustomerGroupPriceDeleteForm form) {
        var entity = require(form.getGroupPriceId(), form.getVersion());
        String beforeSnapshot = snapshot(entity);
        if (customerGroupPriceDao.softDelete(entity.getId(), form.getVersion(), ScmOperator.current()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        entity.setDeleted(true);
        entity.setVersion(entity.getVersion() + 1);
        log(entity, ScmPriceOperationTypeEnum.DELETE, beforeSnapshot);
    }

    private CustomerGroupPriceEntity require(Long groupPriceId, Integer version) {
        var entity = groupPriceId == null ? null : customerGroupPriceDao.selectById(groupPriceId);
        if (entity == null) {
            throw new ScmBusinessException(GROUP_PRICE_NOT_FOUND);
        }
        if (!Objects.equals(version, entity.getVersion())) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        return entity;
    }

    private void lockGroup(Long groupId) {
        if (groupId == null || customerGroupPriceDao.lockGroup(groupId) == null) {
            throw new ScmBusinessException(GROUP_PRICE_NOT_FOUND);
        }
    }

    private void requireNoOverlap(Long groupId, Long spuId, String saleUnit, OffsetDateTime effectiveFrom,
        OffsetDateTime effectiveTo, Long excludedId) {
        if (customerGroupPriceDao.countOverlapping(groupId, spuId, saleUnit, effectiveFrom, effectiveTo,
            excludedId) > 0) {
            throw new ScmBusinessException(GROUP_PRICE_OVERLAP);
        }
    }

    /** 规格只用来确定商品与销售单位；不可售或不存在的规格不允许作为定价依据。 */
    private ProductSkuOptionVO requireSku(Long skuId) {
        var skus = skuId == null ? List.<ProductSkuOptionVO>of() : productSkuOptionDao.selectByIds(List.of(skuId));
        var sku = skus.isEmpty() ? null : skus.getFirst();
        if (sku == null || PriceValidation.unavailable(sku, true) != null) {
            throw new ScmBusinessException(SKU_NOT_SELLABLE);
        }
        if (sku.getSpuId() == null || sku.getSaleUnit() == null || sku.getSaleUnit().isBlank()) {
            throw new ScmBusinessException(GROUP_PRICE_INVALID);
        }
        return sku;
    }

    private void apply(CustomerGroupPriceEntity entity, CustomerGroupPriceAddForm form, ProductSkuOptionVO sku) {
        entity.setGroupId(form.getGroupId());
        entity.setSpuId(sku.getSpuId());
        entity.setSaleUnit(sku.getSaleUnit().trim());
        entity.setUnitPrice(ScmDecimalStrings.parseScale4(form.getUnitPrice()));
        entity.setEffectiveFrom(form.getEffectiveFrom());
        entity.setEffectiveTo(form.getEffectiveTo());
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.setUpdatedBy(ScmOperator.current());
        if (entity.getId() == null) {
            entity.setCreatedAt(entity.getUpdatedAt());
            entity.setCreatedBy(entity.getUpdatedBy());
        }
    }

    private String snapshot(CustomerGroupPriceEntity entity) {
        var fields = new java.util.LinkedHashMap<String, Object>();
        fields.put("id", entity.getId());
        fields.put("groupId", entity.getGroupId());
        fields.put("spuId", entity.getSpuId());
        fields.put("saleUnit", entity.getSaleUnit());
        fields.put("unitPrice", ScmDecimalStrings.format(entity.getUnitPrice()));
        fields.put("effectiveFrom", entity.getEffectiveFrom().toString());
        fields.put("effectiveTo", entity.getEffectiveTo() == null ? null : entity.getEffectiveTo().toString());
        fields.put("version", entity.getVersion());
        fields.put("deleted", entity.getDeleted());
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("价格审计快照序列化失败", ex);
        }
    }

    private void log(CustomerGroupPriceEntity entity, ScmPriceOperationTypeEnum operationType, String beforeSnapshot) {
        customerGroupPriceDao.log(entity.getId(), operationType.name(), ScmOperator.current(), beforeSnapshot,
            snapshot(entity));
    }
}
