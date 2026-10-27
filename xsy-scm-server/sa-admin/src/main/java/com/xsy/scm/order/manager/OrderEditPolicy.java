package com.xsy.scm.order.manager;

import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.order.constant.ScmOrderStatusEnum;
import com.xsy.scm.order.domain.entity.SalesOrderEntity;
import com.xsy.scm.order.permission.OrderPermission;

/**
 * 改单资格的唯一判定口径：前端按钮可不可点、写接口允不允许，都从这里出发，避免两边各写一套条件。
 *
 * <p>
 * 这里只回答「当前调用者能不能改这张单」；待确认订单是否已有资金/库存事实（{@code hasPriceFreezeBlockingFact}） 与优惠冻结这类需要查库的条件仍在写路径里判定，列表不做逐行查库。
 */
public final class OrderEditPolicy {
    private OrderEditPolicy() {
    }

    /**
     * 不可改单的原因；返回 {@code null} 表示当前状态与权限下可以改单。
     */
    public static String blockedReason(SalesOrderEntity order) {
        var status = order.getStatus();
        if (ScmOrderStatusEnum.DRAFT.name().equals(status) || ScmOrderStatusEnum.PENDING.name().equals(status))
            return null;
        if (!ScmOrderStatusEnum.CONFIRMED.name().equals(status))
            return "当前订单状态不允许改单";
        if (Boolean.TRUE.equals(order.getLockedFlag())
            && !ScmDataScopeService.hasPermission(OrderPermission.EDIT_LOCKED))
            return "订单已锁单，请新建加单";
        if (order.getDispatchedAt() != null && !ScmDataScopeService.hasPermission(OrderPermission.EDIT_AFTER_DISPATCH))
            return "订单已发车出库，改单需要发车后改单权限";
        return null;
    }

    public static boolean editable(SalesOrderEntity order) {
        return blockedReason(order) == null;
    }
}
