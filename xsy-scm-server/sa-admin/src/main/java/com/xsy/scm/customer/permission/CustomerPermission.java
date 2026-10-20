package com.xsy.scm.customer.permission;

import com.xsy.scm.common.permission.ScmCrossDomainPermission;

/** Stable permission identifiers published by customer-management APIs. */
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

    /** 客户专属推广二维码的生成、撤销与扫码记录查看。 */
    public static final String PROMOTION_MANAGE = "scm:customer:promotion:manage";

    private CustomerPermission() {
    }
}
