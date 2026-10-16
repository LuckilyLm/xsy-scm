package com.xsy.scm.metrics.domain;

import java.math.BigDecimal;

/** 今日采购金额和单数按采购单提交时间统计，只计算已提交的有效单据；收货量按实际确认时间统计。 */
public record PurchaseMetrics(long todayPurchaseOrderCount, BigDecimal todayPurchaseAmount,
        long totalPurchaseOrderCount, BigDecimal totalPurchaseAmount, long todayReceiptCount, long todaySupplierCount) {
}
