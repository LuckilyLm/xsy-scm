package com.xsy.scm.warehouse.permission;

/**
 * 仓库管理接口使用的权限码。
 */
public final class WarehousePermission {

    public static final String QUERY = "scm:warehouse:query";
    public static final String ADD = "scm:warehouse:add";
    public static final String UPDATE = "scm:warehouse:update";
    public static final String ENABLE = "scm:warehouse:enable";
    public static final String DISABLE = "scm:warehouse:disable";
    public static final String SCOPE_QUERY = "scm:warehouse:scope:query";
    public static final String SCOPE_UPDATE = "scm:warehouse:scope:update";

    private WarehousePermission() {
    }
}
