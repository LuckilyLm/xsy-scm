package com.xsy.scm.customer.permission;

import com.xsy.scm.common.permission.ScmCrossDomainPermission;

/**
 * 客户管理接口发布的权限码。
 */
public final class CustomerPermission {

    public static final String QUERY = "scm:customer:query";
    public static final String ADD = "scm:customer:add";
    public static final String UPDATE = "scm:customer:update";
    public static final String STATUS = "scm:customer:status";
    public static final String ASSIGN = ScmCrossDomainPermission.CUSTOMER_ASSIGN;
    public static final String DELETE = "scm:customer:delete";
    public static final String TYPE_QUERY = "scm:customer:type:query";
    public static final String TYPE_ADD = "scm:customer:type:add";
    public static final String TYPE_UPDATE = "scm:customer:type:update";
    public static final String TYPE_DELETE = "scm:customer:type:delete";
    public static final String VISIBILITY_QUERY = "scm:customer:visibility:query";
    public static final String GROUP_QUERY = "scm:customer:group:query";
    public static final String GROUP_ADD = "scm:customer:group:add";
    public static final String GROUP_UPDATE = "scm:customer:group:update";
    public static final String GROUP_DELETE = "scm:customer:group:delete";

    /** 客户专属推广二维码的生成、撤销与扫码记录查看。 */
    public static final String PROMOTION_MANAGE = "scm:customer:promotion:manage";

    private CustomerPermission() {
    }
}
