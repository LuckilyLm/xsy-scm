package com.xsy.scm.metrics.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 趋势按天由 SQL 汇总，服务端整理为前端图表需要的序列。销售额和订单数使用订单确认时间，与今日指标保持一致。
 */
public record TrendMetrics(String range, List<String> dates, List<String> fullDates, List<BigDecimal> sales,
    List<Long> orders, List<BigDecimal> purchaseAmounts, List<Long> purchaseOrders, List<BigDecimal> inventoryQuantity,
    List<BigDecimal> inboundQuantity, List<BigDecimal> outboundQuantity) {
}
