package com.xianshuyuan.scm.product.converter;

import com.xianshuyuan.scm.product.dto.ProductSaveRequest;
import com.xianshuyuan.scm.product.dto.ProductSkuSaveRequest;
import com.xianshuyuan.scm.product.entity.ProductSkuEntity;
import com.xianshuyuan.scm.product.entity.ProductSpuEntity;
import com.xianshuyuan.scm.product.service.ProductAggregateValidator;

import java.util.LinkedHashMap;

public final class ProductConverter {

    private ProductConverter() {
    }

    public static ProductSpuEntity toSpu(ProductSaveRequest request) {
        ProductSpuEntity entity = new ProductSpuEntity();
        entity.setVersion(request.version());
        entity.setSpuCode(ProductAggregateValidator.normalizeCode(request.spuCode()));
        entity.setName(request.name().trim());
        entity.setAlias(ProductAggregateValidator.trimToNull(request.alias()));
        entity.setCategoryId(request.categoryId());
        entity.setDescription(ProductAggregateValidator.trimToNull(request.description()));
        entity.setStatus(request.status());
        entity.setUpdatedBy("SYSTEM");
        return entity;
    }

    public static ProductSkuEntity toSku(long spuId, ProductSkuSaveRequest request) {
        ProductSkuEntity entity = new ProductSkuEntity();
        entity.setId(request.id());
        entity.setSpuId(spuId);
        entity.setVersion(request.version());
        entity.setSkuCode(ProductAggregateValidator.normalizeCode(request.skuCode()));
        entity.setBarcode(ProductAggregateValidator.trimToNull(request.barcode()));
        entity.setSpecName(request.specName().trim());
        entity.setSpecValues(new LinkedHashMap<>(request.specValues()));
        entity.setSaleUnit(request.saleUnit().trim());
        entity.setProductType(request.productType());
        entity.setMarketPrice(request.marketPrice());
        entity.setStatus(request.status());
        entity.setDefaultSku(request.defaultSku());
        entity.setSortOrder(request.sortOrder());
        entity.setUpdatedBy("SYSTEM");
        return entity;
    }
}
