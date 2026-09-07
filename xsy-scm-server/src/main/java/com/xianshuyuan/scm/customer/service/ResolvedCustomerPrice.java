package com.xianshuyuan.scm.customer.service;

import java.math.BigDecimal;

public record ResolvedCustomerPrice(Long skuId, BigDecimal unitPrice, PriceSource source, Long sourceRecordId) {
}
