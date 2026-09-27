package com.xsy.scm.product.service;

import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.common.constant.ScmShelfStatusEnum;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.product.dao.ProductSpuDao;
import com.xsy.scm.product.domain.entity.ProductSkuEntity;
import com.xsy.scm.product.domain.entity.ProductSpuEntity;
import com.xsy.scm.product.constant.ScmProductMasterStatusEnum;
import com.xsy.scm.product.domain.form.ProductDeleteForm;
import com.xsy.scm.product.domain.form.ProductSkuForm;
import com.xsy.scm.product.domain.form.ProductSpuAddForm;
import com.xsy.scm.product.domain.form.ProductSpuUpdateForm;
import com.xsy.scm.product.domain.form.ProductStatusForm;
import com.xsy.scm.product.manager.ProductAggregateValidator;
import com.xsy.scm.product.manager.ProductImageChangeSet;
import com.xsy.scm.product.manager.ProductImageSyncManager;
import com.xsy.scm.product.manager.ProductSkuChangeSet;
import com.xsy.scm.product.manager.ProductSkuSyncManager;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.xsy.scm.product.constant.ProductErrorCode.MASTER_STATUS_SALE_CONFLICT;
import static com.xsy.scm.product.constant.ProductErrorCode.PRODUCT_BUSINESS_REFERENCED;
import static com.xsy.scm.product.constant.ProductErrorCode.PRODUCT_CODE_DUPLICATE;
import static com.xsy.scm.product.constant.ProductErrorCode.PRODUCT_NOT_FOUND;
import static com.xsy.scm.product.constant.ProductErrorCode.SKU_BARCODE_DUPLICATE;
import static com.xsy.scm.product.constant.ProductErrorCode.SKU_CODE_DUPLICATE;
import static com.xsy.scm.product.constant.ProductErrorCode.VERSION_CONFLICT;
import static com.xsy.scm.product.manager.ProductAggregateValidator.trimToNull;

@Service
@RequiredArgsConstructor
public class ProductSpuService {
    private final ProductSpuDao productSpuDao;
    private final ProductCategoryService productCategoryService;
    private final ProductAggregateValidator productAggregateValidator;
    private final ProductSkuSyncManager productSkuSyncManager;
    private final ProductImageSyncManager productImageSyncManager;
    private final ProductUomService productUomService;
    private final ProductTagService productTagService;

    @Transactional
    public Long add(ProductSpuAddForm form) {
        productAggregateValidator.validateSpu(form);
        productCategoryService.requireSelectableCategory(form.getCategoryId());
        productUomService.assertUsable(form.getSkuList().stream().map(ProductSkuForm::getSaleUnit).toList());
        productTagService.assertUsable(form.getTagIds());
        var skuChanges = ProductSkuChangeSet.between(List.of(), form.getSkuList());
        var imageChanges = ProductImageChangeSet.between(List.of(), form.getImages());
        var entity = new ProductSpuEntity();
        apply(entity, form);
        entity.setCreatedAt(entity.getUpdatedAt());
        entity.setCreatedBy(entity.getUpdatedBy());
        try {
            productSpuDao.insert(entity);
            productSkuSyncManager.sync(entity.getId(), skuChanges);
            productImageSyncManager.sync(entity.getId(), imageChanges);
        } catch (DuplicateKeyException e) {
            throw duplicate(e);
        }
        productTagService.replaceTags(List.of(entity.getId()), form.getTagIds());
        return entity.getId();
    }

    @Transactional
    public void update(ProductSpuUpdateForm form) {
        productAggregateValidator.validateSpu(form);
        productCategoryService.requireSelectableCategory(form.getCategoryId());
        var entity = require(form.getSpuId(), form.getVersion());
        var existing = productSkuSyncManager.existing(entity.getId());
        var skuChanges = ProductSkuChangeSet.between(existing, form.getSkuList());
        productUomService.assertUsable(changedUnits(existing, skuChanges));
        productTagService.assertNewBindings(entity.getId(), form.getTagIds());
        var imageChanges =
                ProductImageChangeSet.between(productImageSyncManager.existing(entity.getId()), form.getImages());
        apply(entity, form);
        entity.setVersion(form.getVersion());
        try {
            if (productSpuDao.updateById(entity) != 1) throw new ScmBusinessException(VERSION_CONFLICT);
            productSkuSyncManager.sync(entity.getId(), skuChanges);
            productImageSyncManager.sync(entity.getId(), imageChanges);
        } catch (DuplicateKeyException e) {
            throw duplicate(e);
        }
        productTagService.replaceTags(List.of(entity.getId()), form.getTagIds());
    }

    @Transactional
    public void updateStatus(ProductStatusForm form) {
        var entity = require(form.getSpuId(), form.getVersion());
        // 归档商品退出经营，DB 有 CHECK 兜底；这里先给出可解释的业务错误。
        assertSaleCompatible(entity.getMasterStatus(), form.getStatus());
        entity.setStatus(form.getStatus());
        stamp(entity);
        if (productSpuDao.updateById(entity) != 1) throw new ScmBusinessException(VERSION_CONFLICT);
    }

    @Transactional
    public void delete(ProductDeleteForm form) {
        var entity = require(form.getSpuId(), form.getVersion());
        if (productSpuDao.hasBusinessReference(entity.getId())) {
            throw new ScmBusinessException(PRODUCT_BUSINESS_REFERENCED);
        }
        stamp(entity);
        if (productSpuDao.updateById(entity) != 1) throw new ScmBusinessException(VERSION_CONFLICT);
        productTagService.untagProducts(List.of(entity.getId()));
        productSkuSyncManager.remove(
                productSkuSyncManager.existing(entity.getId()).stream().map(s -> s.getId()).toList());
        productImageSyncManager.remove(
                productImageSyncManager.existing(entity.getId()).stream().map(i -> i.getId()).toList());
        productSpuDao.deleteById(entity.getId());
    }

    private ProductSpuEntity require(Long id, Integer version) {
        var entity = productSpuDao.selectById(id);
        if (entity == null) throw new ScmBusinessException(PRODUCT_NOT_FOUND);
        if (version == null || !Objects.equals(entity.getVersion(), version))
            throw new ScmBusinessException(VERSION_CONFLICT);
        return entity;
    }

    private void apply(ProductSpuEntity entity, ProductSpuAddForm form) {
        BeanUtils.copyProperties(form, entity, "version", "masterStatus");
        entity.setSpuCode(ProductAggregateValidator.normalizeCode(form.getSpuCode()));
        entity.setName(form.getName().trim());
        entity.setAlias(trimToNull(form.getAlias()));
        entity.setDescription(trimToNull(form.getDescription()));
        entity.setMnemonicCode(trimToNull(form.getMnemonicCode()));
        entity.setBrandName(trimToNull(form.getBrandName()));
        entity.setOrigin(trimToNull(form.getOrigin()));
        entity.setInvoiceName(trimToNull(form.getInvoiceName()));
        entity.setTaxCategoryCode(trimToNull(form.getTaxCategoryCode()));
        // 主档状态与在售状态正交：表单不传就是「不改」，新增时才落到 ENABLED。
        if (form.getMasterStatus() != null) entity.setMasterStatus(form.getMasterStatus());
        if (entity.getMasterStatus() == null) entity.setMasterStatus(ScmEnableStatusEnum.ENABLED.name());
        assertSaleCompatible(entity.getMasterStatus(), entity.getStatus());
        stamp(entity);
    }

    private void assertSaleCompatible(String masterStatus, String status) {
        if (ScmProductMasterStatusEnum.ARCHIVED.name().equals(masterStatus)
                && ScmShelfStatusEnum.ON_SHELF.name().equals(status))
            throw new ScmBusinessException(MASTER_STATUS_SALE_CONFLICT);
    }

    /**
     * 只复核新增或改过单位的 SKU：单位字典晚于既有商品建立，
     * 未改动的历史值即便不在字典里也必须允许原样保存，否则每次编辑都会被拦住。
     */
    private Collection<String> changedUnits(List<ProductSkuEntity> existing, ProductSkuChangeSet changes) {
        Map<Long, String> before = new HashMap<>();
        existing.forEach(s -> before.put(s.getId(), s.getSaleUnit()));
        List<String> units = new ArrayList<>();
        changes.inserted().forEach(s -> units.add(s.getSaleUnit()));
        changes.updated().forEach(s -> {
            var skuId = s.getSkuId();
            if (skuId == null || !Objects.equals(before.get(skuId), trimToNull(s.getSaleUnit())))
                units.add(s.getSaleUnit());
        });
        return units;
    }

    private void stamp(ProductSpuEntity entity) {
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.setUpdatedBy(ScmOperator.current());
    }

    private ScmBusinessException duplicate(DuplicateKeyException e) {
        String constraint = e.getMostSpecificCause().getMessage();
        if (constraint != null && constraint.contains("uk_product_sku_code"))
            return new ScmBusinessException(SKU_CODE_DUPLICATE);
        if (constraint != null && constraint.contains("uk_product_sku_barcode"))
            return new ScmBusinessException(SKU_BARCODE_DUPLICATE);
        return new ScmBusinessException(PRODUCT_CODE_DUPLICATE);
    }
}
