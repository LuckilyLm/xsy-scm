package net.lab1024.sa.admin.module.scm.pricing.domain.vo;

import java.time.OffsetDateTime;
import java.util.List;
public record PriceResolveResultVO(Long customerId, Long customerTypeId, String customerTypeName, OffsetDateTime at, List<ResolvedPriceVO> items) {}
