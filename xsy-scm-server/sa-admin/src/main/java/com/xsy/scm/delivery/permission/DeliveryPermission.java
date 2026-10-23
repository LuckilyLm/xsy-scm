package com.xsy.scm.delivery.permission;

import com.xsy.scm.common.permission.ScmCrossDomainPermission;

/**
 * 配送域对外发布的稳定权限标识。
 */
public final class DeliveryPermission {

    public static final String DRIVER_QUERY = "scm:delivery:driver:query";

    public static final String DRIVER_EDIT = "scm:delivery:driver:edit";

    public static final String VEHICLE_QUERY = "scm:delivery:vehicle:query";

    public static final String VEHICLE_EDIT = "scm:delivery:vehicle:edit";

    public static final String ROUTE_QUERY = "scm:delivery:route:query";

    public static final String ROUTE_ADD = "scm:delivery:route:add";

    public static final String ROUTE_UPDATE = "scm:delivery:route:update";

    public static final String ROUTE_PLAN = "scm:delivery:route:plan";

    public static final String ROUTE_CANCEL = "scm:delivery:route:cancel";

    public static final String ROUTE_DISPATCH = "scm:delivery:route:dispatch";

    public static final String ROUTE_COMPLETE = "scm:delivery:route:complete";

    public static final String ROUTE_PRINT = "scm:delivery:route:print";

    public static final String ORDER_SIGN = "scm:delivery:order:sign";

    /**
     * 排线建议的查询、生成、应用分开授权。
     *
     * <p>
     * 生成只算一份建议，不碰线路；应用才写回停靠顺序。合成一个权限的话，只想看看怎么排的人也能顺手改线路。
     */
    public static final String PLAN_QUERY = "scm:delivery:plan:query";

    public static final String PLAN_PROPOSE = "scm:delivery:plan:propose";

    public static final String PLAN_APPLY = "scm:delivery:plan:apply";

    public static final String SCOPE_ALL_QUERY = ScmCrossDomainPermission.DELIVERY_SCOPE_ALL_QUERY;

    public static final String AMOUNT_QUERY = ScmCrossDomainPermission.DELIVERY_AMOUNT_QUERY;

    private DeliveryPermission() {
    }
}
