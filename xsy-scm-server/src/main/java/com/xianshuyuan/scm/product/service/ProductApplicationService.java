package com.xianshuyuan.scm.product.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.product.converter.ProductConverter;
import com.xianshuyuan.scm.product.dto.ProductSaveRequest;
import com.xianshuyuan.scm.product.dto.ProductSkuSaveRequest;
import com.xianshuyuan.scm.product.entity.ProductSkuEntity;
import com.xianshuyuan.scm.product.entity.ProductSpuEntity;
import com.xianshuyuan.scm.product.entity.ShelfStatus;
import com.xianshuyuan.scm.product.mapper.ProductSkuMapper;
import com.xianshuyuan.scm.product.mapper.ProductSpuMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductApplicationService.class);

    private final ProductSpuMapper spuMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductCategoryService categoryService;
    private final ProductAggregateValidator validator;

    public ProductApplicationService(
            ProductSpuMapper spuMapper,
            ProductSkuMapper skuMapper,
            ProductCategoryService categoryService,
            ProductAggregateValidator validator
    ) {
        this.spuMapper = spuMapper;
        this.skuMapper = skuMapper;
        this.categoryService = categoryService;
        this.validator = validator;
    }

    @Transactional(rollbackFor = Exception.class)
    public long create(ProductSaveRequest request) {
        validator.validate(request);
        categoryService.requireSelectableCategory(request.categoryId());

        ProductSpuEntity spu = ProductConverter.toSpu(request);
        spu.setVersion(0);
        spu.setDeleted(false);
        spu.setCreatedBy("SYSTEM");
        spuMapper.insert(spu);
        for (ProductSkuSaveRequest skuRequest : request.skus()) {
            ProductSkuEntity sku = ProductConverter.toSku(spu.getId(), skuRequest);
            sku.setVersion(0);
            sku.setDeleted(false);
            sku.setCreatedBy("SYSTEM");
            skuMapper.insert(sku);
        }
        LOGGER.info("product_created operator=SYSTEM spuId={} skuCount={}",
                spu.getId(), request.skus().size());
        return spu.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(long spuId, ProductSaveRequest request) {
        validator.validate(request);
        categoryService.requireSelectableCategory(request.categoryId());
        requireSpu(spuId);
        if (request.version() == null) {
            throw new BusinessException(ProductErrorCodes.VERSION_CONFLICT);
        }

        List<ProductSkuEntity> existing = skuMapper.selectActiveBySpuId(spuId);
        ProductSkuChangeSet changes = ProductSkuChangeSet.between(existing, request.skus());

        ProductSpuEntity spu = ProductConverter.toSpu(request);
        spu.setId(spuId);
        if (spuMapper.updateById(spu) != 1) {
            throw new BusinessException(ProductErrorCodes.VERSION_CONFLICT);
        }

        skuMapper.clearDefault(spuId);
        for (ProductSkuSaveRequest updateRequest : changes.updated()) {
            if (updateRequest.version() == null) {
                throw new BusinessException(ProductErrorCodes.VERSION_CONFLICT);
            }
            ProductSkuEntity sku = ProductConverter.toSku(spuId, updateRequest);
            if (skuMapper.updateById(sku) != 1) {
                throw new BusinessException(ProductErrorCodes.VERSION_CONFLICT);
            }
        }
        for (ProductSkuSaveRequest insertRequest : changes.inserted()) {
            ProductSkuEntity sku = ProductConverter.toSku(spuId, insertRequest);
            sku.setVersion(0);
            sku.setDeleted(false);
            sku.setCreatedBy("SYSTEM");
            skuMapper.insert(sku);
        }
        if (!changes.removedIds().isEmpty()) {
            skuMapper.deleteByIds(changes.removedIds());
        }
        LOGGER.info(
                "product_updated operator=SYSTEM spuId={} inserted={} updated={} removed={}",
                spuId, changes.inserted().size(), changes.updated().size(), changes.removedIds().size()
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(long spuId, int version, ShelfStatus status) {
        ProductSpuEntity existing = requireSpu(spuId);
        existing.setVersion(version);
        existing.setStatus(status);
        existing.setUpdatedBy("SYSTEM");
        if (spuMapper.updateById(existing) != 1) {
            throw new BusinessException(ProductErrorCodes.VERSION_CONFLICT);
        }
        LOGGER.info("product_status_changed operator=SYSTEM spuId={} status={}", spuId, status);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(long spuId, int version) {
        ProductSpuEntity existing = requireSpu(spuId);
        if (!existing.getVersion().equals(version)) {
            throw new BusinessException(ProductErrorCodes.VERSION_CONFLICT);
        }
        List<ProductSkuEntity> activeSkus = skuMapper.selectActiveBySpuId(spuId);
        if (!activeSkus.isEmpty()) {
            skuMapper.deleteByIds(activeSkus.stream().map(ProductSkuEntity::getId).toList());
        }
        spuMapper.deleteById(spuId);
        LOGGER.info("product_deleted operator=SYSTEM spuId={} skuCount={}", spuId, activeSkus.size());
    }

    public ProductSpuEntity requireSpu(long spuId) {
        ProductSpuEntity spu = spuMapper.selectById(spuId);
        if (spu == null || Boolean.TRUE.equals(spu.getDeleted())) {
            throw new BusinessException(ProductErrorCodes.PRODUCT_NOT_FOUND);
        }
        return spu;
    }
}
