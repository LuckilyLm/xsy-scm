package com.xsy.scm.common.permission;

/**
 * 一个 SCM 域引用另一个域权限码时使用，避免模块间直接依赖。
 */
public final class ScmCrossDomainPermission {

    public static final String CUSTOMER_SCOPE_ALL_QUERY = "scm:customer:scope:all:query";

    public static final String ORDER_SCOPE_ALL_QUERY = "scm:order:scope:all:query";

    public static final String CUSTOMER_ASSIGN = "scm:customer:assign";

    public static final String REPORT_COST_QUERY = "scm:report:cost:query";

    public static final String INVENTORY_BALANCE_QUERY = "scm:inventory:balance:query";

    public static final String INVENTORY_SCOPE_ALL_QUERY = "scm:inventory:scope:all:query";

    public static final String PURCHASE_ASSIGN = "scm:purchase:assign";

    public static final String PURCHASE_SCOPE_ALL_QUERY = "scm:purchase:scope:all:query";

    public static final String DELIVERY_SCOPE_ALL_QUERY = "scm:delivery:scope:all:query";

    public static final String DELIVERY_AMOUNT_QUERY = "scm:delivery:amount:query";

    private ScmCrossDomainPermission() {
    }
}
