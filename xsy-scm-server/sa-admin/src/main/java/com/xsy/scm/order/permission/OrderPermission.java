package com.xsy.scm.order.permission;

/** Stable permission identifiers published by the sales-order API. */
public final class OrderPermission {

    public static final String QUERY = "scm:order:query";

    public static final String LOG_QUERY = "scm:order:log:query";

    public static final String ADD = "scm:order:add";

    public static final String UPDATE = "scm:order:update";

    public static final String SUBMIT = "scm:order:submit";

    public static final String CONFIRM = "scm:order:confirm";

    public static final String CANCEL = "scm:order:cancel";

    public static final String ACTUAL_QUANTITY = "scm:order:actual-quantity";

    public static final String PRICE_OVERRIDE = "scm:order:price-override";

    public static final String DELETE = "scm:order:delete";

    public static final String RESERVE_STOCK = "scm:order:reserve-stock";

    public static final String IMPORT = "scm:order:import";

    public static final String RETURN_QUERY = "scm:order:return:query";

    public static final String RETURN_ADD = "scm:order:return:add";

    public static final String RETURN_APPROVE = "scm:order:return:approve";

    public static final String RETURN_REJECT = "scm:order:return:reject";

    public static final String RETURN_CANCEL = "scm:order:return:cancel";

    public static final String REFUND_QUERY = "scm:order:refund:query";

    public static final String REFUND_COMPLETE = "scm:order:refund:complete";

    private OrderPermission() {
    }
}
