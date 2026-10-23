package com.xsy.scm.metrics.domain;

import java.math.BigDecimal;

/**
 * 区间内的已提交采购数据：采购单数与金额。
 *
 * <p>
 * 两个字段都按 {@code submitted_at} 统计，且只算已提交状态，报表采购分析与首页 / 大屏共用这份定义。
 */
public record PurchaseRangeMetrics(long orderCount, BigDecimal amount) {
}
