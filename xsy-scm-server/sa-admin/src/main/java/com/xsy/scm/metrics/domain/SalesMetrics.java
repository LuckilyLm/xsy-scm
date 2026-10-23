package com.xsy.scm.metrics.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 销售额、订单数和成交客户数按确认时间统计；下单金额按创建时间统计。两个时间基准不能混用。
 */
public record SalesMetrics(long todayOrderCount, BigDecimal todayOrderedAmount, BigDecimal todaySettlementAmount,
    long totalOrderCount, BigDecimal totalSettlementAmount, long todayCustomerCount, List<RankItem> topCustomers,
    List<RankItem> topProducts) {
}
