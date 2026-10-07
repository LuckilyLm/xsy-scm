package com.xsy.scm.metrics.domain;

/**
 * 销售指标的附加筛选维度。
 *
 * <p>
 * 报表概览允许按客户 / 业务员 / 订单来源收窄；首页与大屏传 {@link #NONE}。这些是订单自身的业务维度， 不是某一端的私有筛选 —— 所以它们作为指标入参出现，而不是让报表另写一份 SQL。
 */
public record SalesFilter(Long customerId, Long sellerId, String orderSource) {

    public static final SalesFilter NONE = new SalesFilter(null, null, null);
}
