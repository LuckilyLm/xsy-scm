package com.xsy.scm.report.constant;

import com.xsy.scm.common.permission.ScmCrossDomainPermission;

/** Stable permission identifiers used by report endpoints. */
public final class ScmReportPermission {

    public static final String OVERVIEW_QUERY = "scm:report:overview:query";
    public static final String SALES_QUERY = "scm:report:sales:query";
    public static final String PURCHASE_QUERY = "scm:report:purchase:query";
    public static final String INVENTORY_QUERY = "scm:report:inventory:query";
    public static final String FINANCE_QUERY = "scm:report:finance:query";
    public static final String FINANCE_AGING_QUERY = "scm:report:finance:aging:query";
    /** Financial margin access; endpoints also require the independent cost permission. */
    public static final String FINANCE_PROFIT_QUERY = "scm:report:finance:profit:query";
    public static final String COST_QUERY = ScmCrossDomainPermission.REPORT_COST_QUERY;
    public static final String EXPORT = "scm:report:export";

    private ScmReportPermission() {
    }
}
