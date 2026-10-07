package com.xsy.scm.metrics.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 趋势序列（已按序列转置）。
 *
 * <p>
 * 转置放在服务层，SQL 保持「一天一行」这种最好核对形状；ECharts 的 series 是按序列组织的，前端拿到即可直接画。 {@code sales} / {@code orders} 走确认轴，与
 * {@link SalesMetrics} 的今日值同口径 —— 大屏的环比正是拿这两者相减。
 */
public record TrendMetrics(String range, List<String> dates, List<String> fullDates, List<BigDecimal> sales,
        List<Long> orders, List<BigDecimal> purchaseAmounts, List<Long> purchaseOrders,
        List<BigDecimal> inventoryQuantity, List<BigDecimal> inboundQuantity, List<BigDecimal> outboundQuantity) {
}
