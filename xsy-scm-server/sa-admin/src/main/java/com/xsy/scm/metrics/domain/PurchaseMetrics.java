package com.xsy.scm.metrics.domain;

import java.math.BigDecimal;

/**
 * 采购指标。
 *
 * <p>
 * 今日采购单数与金额走<b>提交轴</b>（{@code submitted_at}）且只统计已提交状态（非 {@code DRAFT} 且非 {@code CANCELLED}，由
 * {@code ScmPurchaseStatusEnum.committedNames()} 派生）：提交是采购单的业务生效点，草稿只是本地的、取消的已经退出履约链路。 这与报表的「已提交采购金额」是同一个口径。
 *
 * <p>
 * {@code todayReceiptCount} 走<b>确认轴</b>（{@code confirmed_at} + {@code status = 'CONFIRMED'}）：草稿收货单还没提交。
 * 累计口径（{@code total*}）与今日同源，也是已提交口径。
 */
public record PurchaseMetrics(long todayPurchaseOrderCount, BigDecimal todayPurchaseAmount,
        long totalPurchaseOrderCount, BigDecimal totalPurchaseAmount, long todayReceiptCount, long todaySupplierCount) {
}
