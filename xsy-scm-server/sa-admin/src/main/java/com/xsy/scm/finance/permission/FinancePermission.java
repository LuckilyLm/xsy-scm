package com.xsy.scm.finance.permission;

/** Stable permission identifiers for protected finance endpoints. */
public final class FinancePermission {

    public static final String RECEIVABLE_QUERY = "scm:finance:receivable:query";
    public static final String PAYABLE_QUERY = "scm:finance:payable:query";
    public static final String RECEIPT_QUERY = "scm:finance:receipt:query";
    public static final String PAYMENT_QUERY = "scm:finance:payment:query";
    public static final String WRITE_OFF_QUERY = "scm:finance:write-off:query";

    public static final String RECEIPT_ADD = "scm:finance:receipt:add";
    public static final String PAYMENT_ADD = "scm:finance:payment:add";
    public static final String WRITE_OFF_ADD = "scm:finance:write-off:add";
    public static final String PAYABLE_RED = "scm:finance:payable:red";

    public static final String WRITE_OFF_REVERSE = "scm:finance:write-off:reverse";
    public static final String RECEIPT_REVERSE = "scm:finance:receipt:reverse";
    public static final String PAYMENT_REVERSE = "scm:finance:payment:reverse";
    public static final String EXPORT = "scm:finance:export";

    private FinancePermission() {
    }
}
