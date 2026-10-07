package com.xsy.scm.metrics.domain;

import java.math.BigDecimal;

/**
 * 趋势的一天一行。
 *
 * <p>
 * 日期轴由 SQL 的 {@code generate_series} 生成：没有任何单据的日期也会返回一行（全为 0），否则前端折线会在缺数据的日期上错位。
 */
public record TrendPoint(String date, String label, Long orders, BigDecimal sales, Long purchaseOrders,
        BigDecimal purchaseAmounts, BigDecimal inventoryQuantity, BigDecimal inboundQuantity,
        BigDecimal outboundQuantity) {
}
