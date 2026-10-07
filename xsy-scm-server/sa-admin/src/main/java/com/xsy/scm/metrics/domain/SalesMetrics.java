package com.xsy.scm.metrics.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 销售经营指标。
 *
 * <p>
 * 字段名自带口径：{@code todaySettlementAmount}、{@code todayOrderCount}、{@code todayCustomerCount} 与两张排行走<b>确认轴</b>
 * （{@code confirmed_at} 落在区间内），{@code todayOrderedAmount} 走<b>创建轴</b>（{@code created_at}）。两组不是同一个数，
 * 订单数与成交客户数必须与销售额同轴，否则客单价与环比会变成跨口径的比值。
 */
public record SalesMetrics(long todayOrderCount, BigDecimal todayOrderedAmount, BigDecimal todaySettlementAmount,
        long totalOrderCount, BigDecimal totalSettlementAmount, long todayCustomerCount, List<RankItem> topCustomers,
        List<RankItem> topProducts) {
}
