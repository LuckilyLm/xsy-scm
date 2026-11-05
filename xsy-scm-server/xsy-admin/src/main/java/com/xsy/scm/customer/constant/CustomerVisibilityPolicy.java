package com.xsy.scm.customer.constant;

/**
 * 客户可见性策略的取值，与 {@code ck_customer_visibility_policy} 约束一致。
 */
public final class CustomerVisibilityPolicy {

    public static final String ALL_ENABLED = "ALL_ENABLED";
    public static final String ALLOWLIST = "ALLOWLIST";
    public static final String PATTERN = ALL_ENABLED + "|" + ALLOWLIST;

    private CustomerVisibilityPolicy() {
    }
}
