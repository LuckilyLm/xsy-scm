package com.xsy.scm.businesscategory.permission;

/**
 * 客户 / 供应商业务分类接口发布的权限码。
 */
public final class BusinessCategoryPermission {

    public static final String QUERY = "scm:business-category:query";
    public static final String ADD = "scm:business-category:add";
    public static final String UPDATE = "scm:business-category:update";
    public static final String DELETE = "scm:business-category:delete";

    private BusinessCategoryPermission() {
    }
}
