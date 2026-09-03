package com.xianshuyuan.scm.customer.vo;

public record OrderableSkuResponse(
    Long skuId,
    Long spuId,
    String productName,
    String skuCode,
    String specName,
    java.util.Map<String, String> specValues,
    String saleUnit,
    com.xianshuyuan.scm.product.entity.ProductType productType,
    String marketPrice,
    com.xianshuyuan.scm.product.entity.ShelfStatus status
) {}
