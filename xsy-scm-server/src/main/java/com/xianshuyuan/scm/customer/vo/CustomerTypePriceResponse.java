package com.xianshuyuan.scm.customer.vo;

import java.time.OffsetDateTime;

public record CustomerTypePriceResponse(Long id, Integer version, Long customerTypeId, Long skuId,
                                        String unitPrice, OffsetDateTime effectiveFrom, OffsetDateTime effectiveTo) {
}
