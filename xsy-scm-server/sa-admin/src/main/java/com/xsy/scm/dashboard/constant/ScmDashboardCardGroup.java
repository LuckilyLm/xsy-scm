package com.xsy.scm.dashboard.constant;

/**
 * 来自同一统计结果的卡片只查询一次；分组用于减少重复数据库聚合。
 */
public enum ScmDashboardCardGroup {

    /**
     * 销售聚合：今日销售额与今日订单数。
     */
    SALES,

    /**
     * 采购聚合：今日采购额与今日收货单数。
     */
    PURCHASE,

    /**
     * 库存预警：取值是预警列表的分页总数，走独立查询。
     */
    INVENTORY_WARNING
}
