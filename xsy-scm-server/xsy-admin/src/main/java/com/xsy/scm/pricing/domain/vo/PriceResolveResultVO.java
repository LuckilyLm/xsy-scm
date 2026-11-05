package com.xsy.scm.pricing.domain.vo;

import java.time.OffsetDateTime;
import java.util.List;

public record PriceResolveResultVO(Long customerId, OffsetDateTime at, List<ResolvedPriceVO> items) {
}
