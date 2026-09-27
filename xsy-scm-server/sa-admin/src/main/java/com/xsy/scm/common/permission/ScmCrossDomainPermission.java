package com.xsy.scm.common.permission;

/** Permission identifiers that one SCM domain needs to reference without importing another domain. */
public final class ScmCrossDomainPermission {

    public static final String INVENTORY_BALANCE_QUERY = "scm:inventory:balance:query";

    public static final String INVENTORY_SCOPE_ALL_QUERY = "scm:inventory:scope:all:query";

    public static final String PURCHASE_ASSIGN = "scm:purchase:assign";

    public static final String PURCHASE_SCOPE_ALL_QUERY = "scm:purchase:scope:all:query";

    private ScmCrossDomainPermission() {
    }
}
