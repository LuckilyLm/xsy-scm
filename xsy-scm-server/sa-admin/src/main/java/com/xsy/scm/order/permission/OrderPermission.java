package com.xsy.scm.order.permission;

/**
 * 订单接口对外发布的权限码（稳定标识）。
 */
public final class OrderPermission {

    public static final String QUERY = "scm:order:query";
    public static final String ASSIGN = "scm:order:assign";

    public static final String LOG_QUERY = "scm:order:log:query";

    public static final String ADD = "scm:order:add";

    public static final String UPDATE = "scm:order:update";

    public static final String SUBMIT = "scm:order:submit";

    public static final String CONFIRM = "scm:order:confirm";

    public static final String CANCEL = "scm:order:cancel";

    public static final String ACTUAL_QUANTITY = "scm:order:actual-quantity";

    public static final String PRICE_OVERRIDE = "scm:order:price-override";

    public static final String DELETE = "scm:order:delete";

    public static final String RESERVE_STOCK = "scm:order:reserve-stock";

    /** 手动锁单与自动锁单的办理入口；解锁是独立权限，避免同一角色既能锁又能随手解锁。 */
    public static final String LOCK = "scm:order:lock";

    public static final String UNLOCK = "scm:order:unlock";

    /** 发车出库后仍要改单的窄口子；没有它，发车后的改单请求直接拒绝。 */
    public static final String EDIT_AFTER_DISPATCH = "scm:order:edit-after-dispatch";

    /** 锁单后仍要改单的窄口子。 */
    public static final String EDIT_LOCKED = "scm:order:edit-locked";

    public static final String IMPORT = "scm:order:import";

    public static final String RETURN_QUERY = "scm:order:return:query";

    public static final String RETURN_ADD = "scm:order:return:add";

    public static final String RETURN_APPROVE = "scm:order:return:approve";

    public static final String RETURN_REJECT = "scm:order:return:reject";

    public static final String RETURN_CANCEL = "scm:order:return:cancel";

    public static final String RETURN_RECEIVE = "scm:order:return:receive";

    public static final String CREDIT_OVERRIDE = "scm:order:credit:override";

    public static final String REFUND_QUERY = "scm:order:refund:query";

    public static final String REFUND_COMPLETE = "scm:order:refund:complete";

    private OrderPermission() {
    }
}
