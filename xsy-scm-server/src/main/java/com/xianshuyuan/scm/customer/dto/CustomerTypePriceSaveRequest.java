package com.xianshuyuan.scm.customer.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CustomerTypePriceSaveRequest(
        Integer version,
        @NotNull Long customerTypeId,
        @NotNull Long skuId,
        @NotNull @Digits(integer = 14, fraction = 4) BigDecimal unitPrice,
        @NotNull OffsetDateTime effectiveFrom,
        OffsetDateTime effectiveTo) {
}
