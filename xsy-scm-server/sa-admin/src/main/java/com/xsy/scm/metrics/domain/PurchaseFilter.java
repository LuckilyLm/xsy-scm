package com.xsy.scm.metrics.domain;

/**
 * 采购指标的附加筛选维度。
 *
 * <p>
 * 报表的采购分析允许按供应商 / 采购员 / 仓库收窄；首页与大屏传 {@link #NONE}。与 {@link SalesFilter} 同一条理由： 这些是采购单自身的业务维度，作为指标入参出现，而不是让报表另写一份 SQL。
 */
public record PurchaseFilter(Long supplierId, Long purchaserId, Long warehouseId) {

    public static final PurchaseFilter NONE = new PurchaseFilter(null, null, null);
}
