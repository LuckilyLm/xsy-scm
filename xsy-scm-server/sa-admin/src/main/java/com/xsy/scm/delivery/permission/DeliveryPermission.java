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

    /**
     * 排线建议：查询 / 生成 / 应用分开授权。
     *
     * <p>
     * 「生成」只是算一份建议（不碰线路），「应用」才写回停靠顺序 —— 两者合并会让
     * 「只是想看看怎么排」的人顺手获得改线路的能力。
     */
    public static final String PLAN_QUERY = "scm:delivery:plan:query";

    public static final String PLAN_PROPOSE = "scm:delivery:plan:propose";

    public static final String PLAN_APPLY = "scm:delivery:plan:apply";

    /** 轨迹查询（调度）。 */
    public static final String GPS_QUERY = "scm:delivery:gps:query";

    /** 轨迹上报（司机端 / 设备）。范围由服务端按司机维度判定，不由前端传参决定。 */
    public static final String GPS_REPORT = "scm:delivery:gps:report";

    public static final String SCOPE_ALL_QUERY = ScmCrossDomainPermission.DELIVERY_SCOPE_ALL_QUERY;

    public static final String AMOUNT_QUERY = ScmCrossDomainPermission.DELIVERY_AMOUNT_QUERY;

    private DeliveryPermission() {
    }
}
