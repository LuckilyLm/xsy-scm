package com.xsy.scm.metrics.domain;

import java.math.BigDecimal;

/**
 * 区间内的销售事实：订单数、销售额、成交客户数。
 *
 * <p>
 * 三个字段同轴（{@code confirmed_at}），是报表概览与首页 / 大屏共用的那一份定义。
 */
public record SalesRangeMetrics(long orderCount, BigDecimal settlementAmount, long customerCount) {
}
