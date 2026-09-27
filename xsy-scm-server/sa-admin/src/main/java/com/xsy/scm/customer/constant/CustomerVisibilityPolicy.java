package com.xsy.scm.customer.constant;

/** Stored customer visibility policies, also constrained by {@code ck_customer_visibility_policy}. */
public final class CustomerVisibilityPolicy {

    public static final String ALL_ENABLED = "ALL_ENABLED";
    public static final String ALLOWLIST = "ALLOWLIST";
    public static final String PATTERN = ALL_ENABLED + "|" + ALLOWLIST;

    private CustomerVisibilityPolicy() {
    }
}
