package com.xianshuyuan.scm.customer.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AgreementPriceSaveRequest(Integer version, @NotNull Long customerId, @NotNull Long skuId,
                                        @NotNull @Digits(integer = 14, fraction = 4) BigDecimal unitPrice,
                                        @NotNull OffsetDateTime effectiveFrom, OffsetDateTime effectiveTo) {
}
