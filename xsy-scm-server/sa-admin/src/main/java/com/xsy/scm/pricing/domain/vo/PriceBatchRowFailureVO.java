package com.xsy.scm.pricing.domain.vo;

public record PriceBatchRowFailureVO(Integer rowNumber, Long customerTypeId, Long skuId, int code, String message) {
}
