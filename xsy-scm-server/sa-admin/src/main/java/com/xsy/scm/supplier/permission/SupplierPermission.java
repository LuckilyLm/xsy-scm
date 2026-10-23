package com.xsy.scm.supplier.permission;

/**
 * 供应商管理接口使用的权限码。
 */
public final class SupplierPermission {

    public static final String QUERY = "scm:supplier:query";
    public static final String ADD = "scm:supplier:add";
    public static final String UPDATE = "scm:supplier:update";
    public static final String STATUS = "scm:supplier:status";
    public static final String DELETE = "scm:supplier:delete";
    public static final String SKU_QUERY = "scm:supplier:sku:query";
    public static final String SKU_UPDATE = "scm:supplier:sku:update";

    private SupplierPermission() {
    }
}
