package com.xsy.scm.purchase.permission;

import com.xsy.scm.common.permission.ScmCrossDomainPermission;

/** Stable permission identifiers published by the purchasing API. */
public final class PurchasePermission {

    public static final String DEMAND_QUERY = "scm:purchase:demand:query";

    public static final String DEMAND_GENERATE = "scm:purchase:demand:generate";

    public static final String DEMAND_ALLOCATE = "scm:purchase:demand:allocate";

    public static final String DEMAND_BATCH_CREATE = "scm:purchase:demand:batch:create";

    public static final String DEMAND_BATCH_GENERATE = "scm:purchase:demand:batch:generate";

    public static final String QUERY = "scm:purchase:query";

    public static final String LOG_QUERY = "scm:purchase:log:query";

    public static final String ADD = "scm:purchase:add";

    public static final String UPDATE = "scm:purchase:update";

    public static final String ASSIGN = ScmCrossDomainPermission.PURCHASE_ASSIGN;

    public static final String SUBMIT = "scm:purchase:submit";

    public static final String CANCEL = "scm:purchase:cancel";

    public static final String SHORT_CLOSE = "scm:purchase:short-close";

    public static final String DELETE = "scm:purchase:delete";

    public static final String RECEIPT_QUERY = "scm:purchase:receipt:query";

    public static final String RECEIPT_ADD = "scm:purchase:receipt:add";

    public static final String RECEIPT_UPDATE = "scm:purchase:receipt:update";

    public static final String RECEIPT_CONFIRM = "scm:purchase:receipt:confirm";

    public static final String RECEIPT_PUTAWAY = "scm:purchase:receipt:putaway";

    public static final String RECEIPT_DELETE = "scm:purchase:receipt:delete";

    public static final String SCOPE_ALL_QUERY = ScmCrossDomainPermission.PURCHASE_SCOPE_ALL_QUERY;

    private PurchasePermission() {
    }
}
