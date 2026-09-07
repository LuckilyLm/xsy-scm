package com.xianshuyuan.scm.customer.vo;

import java.time.OffsetDateTime;

public record AgreementPriceResponse(Long id, Integer version, Long customerId, Long skuId, String unitPrice,
                                     OffsetDateTime effectiveFrom, OffsetDateTime effectiveTo) {
}
