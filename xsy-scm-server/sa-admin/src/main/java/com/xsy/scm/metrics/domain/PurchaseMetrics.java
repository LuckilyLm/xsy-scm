package com.xsy.scm.metrics.domain;

import java.math.BigDecimal;

/**
 * 采购指标。
 *
 * <p>
 * 今日采购单数与金额走<b>创建轴</b>（{@code created_at}）且<b>不过滤状态</b>，因此包含草稿与已取消。这与报表的「已提交采购金额」 （{@code submitted_at} +
 * 已提交状态集合）<b>不是同一个口径</b>，两者不能互相替代，命名也必须能区分开。
 */
public record PurchaseMetrics(long todayPurchaseOrderCount, BigDecimal todayPurchaseAmount,
        long totalPurchaseOrderCount, BigDecimal totalPurchaseAmount, long todayReceiptCount, long todaySupplierCount) {
}
