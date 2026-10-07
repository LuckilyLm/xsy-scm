package com.xsy.scm.dashboard.permission;

/** Stable permission identifiers published by the SCM dashboard APIs. */
public final class DashboardPermission {

    public static final String TODO_QUERY = "scm:todo:query";

    /**
     * 首页「供应链工作台」的入口权限。
     *
     * <p>
     * 只授权访问聚合接口本身：每张 KPI 卡片还要再过对应的领域权限（见 {@code ScmDashboardCardEnum}）， 无权卡片整卡省略。它不隐含订单 / 采购 / 收货 / 库存的任何可见性。
     */
    public static final String QUERY = "scm:dashboard:query";

    private DashboardPermission() {
    }
}
