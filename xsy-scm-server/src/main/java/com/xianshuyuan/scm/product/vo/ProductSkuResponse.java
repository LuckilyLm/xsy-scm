package com.xianshuyuan.scm.product.vo;

import com.xianshuyuan.scm.product.entity.ProductType;
import com.xianshuyuan.scm.product.entity.ShelfStatus;

import java.util.Map;

public record ProductSkuResponse(
    long id,
    int version,
    String skuCode,
    String barcode,
    String specName,
    Map<String, String> specValues,
    String saleUnit,
    ProductType productType,
    String marketPrice,
    ShelfStatus status,
    boolean defaultSku,
    int sortOrder
) {
    public ProductSkuResponse {
        specValues = Map.copyOf(specValues);
    }
}
