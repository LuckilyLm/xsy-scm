package com.xianshuyuan.scm.customer.dto;

public record AgreementPricePageQuery(
    long page,
    long pageSize,
    Long customerId,
    Long skuId
) {}
