package com.xianshuyuan.scm.customer.vo;

public record OrderableSkuResponse(
    Long id,
    Long spuId,
    String skuCode,
    String specName,
    String saleUnit,
    String marketPrice
) {}
