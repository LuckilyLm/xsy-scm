package com.xianshuyuan.scm.customer.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CustomerTypePriceBatchRowRequest(
        @Positive int rowNumber,
        @NotNull Long customerTypeId,
        @NotNull Long skuId,
        @NotNull @Digits(integer = 14, fraction = 4) BigDecimal unitPrice,
        @NotNull OffsetDateTime effectiveFrom,
        OffsetDateTime effectiveTo) {
    public CustomerTypePriceSaveRequest toSaveRequest() {
        return new CustomerTypePriceSaveRequest(null, customerTypeId, skuId, unitPrice, effectiveFrom, effectiveTo);
    }
}
