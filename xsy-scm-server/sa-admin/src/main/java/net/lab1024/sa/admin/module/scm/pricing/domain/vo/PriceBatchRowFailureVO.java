package net.lab1024.sa.admin.module.scm.pricing.domain.vo;

public record PriceBatchRowFailureVO(Integer rowNumber, Long customerTypeId, Long skuId, int code, String message) {
}
