package com.xsy.scm.delivery.permission;

import com.xsy.scm.common.permission.ScmCrossDomainPermission;

/** Stable permission identifiers published by the delivery API. */
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

    public static final String SCOPE_ALL_QUERY = ScmCrossDomainPermission.DELIVERY_SCOPE_ALL_QUERY;

    public static final String AMOUNT_QUERY = ScmCrossDomainPermission.DELIVERY_AMOUNT_QUERY;

    private DeliveryPermission() {
    }
}
