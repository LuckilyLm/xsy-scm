package com.xsy.scm.metrics.domain;

import java.math.BigDecimal;

/**
 * 区间内的已提交采购事实：采购单数与金额。
 *
 * <p>
 * 两个字段同轴（{@code submitted_at}）且只算已提交状态，是报表采购分析与首页 / 大屏共用的那一份定义。
 */
public record PurchaseRangeMetrics(long orderCount, BigDecimal amount) {
}
