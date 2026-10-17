package com.xsy.scm.report.constant;

import com.xsy.scm.delivery.permission.DeliveryPermission;
import com.xsy.scm.order.permission.OrderPermission;
import com.xsy.scm.sorting.permission.SortingPermission;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 异常类别映射源域查看权限；报表权限不授予额外业务访问权。 */
@Getter
@RequiredArgsConstructor
public enum ScmOrderExceptionTypeEnum {
    SORTING_SHORTAGE("分拣缺货", SortingPermission.TASK_QUERY),
    SORTING_DIFFERENCE("分拣数量差异", SortingPermission.TASK_QUERY),
    ORDER_QUANTITY_DIFFERENCE("订单实量差异", OrderPermission.QUERY),
    DELIVERY_EXCEPTION("配送异常签收", DeliveryPermission.ROUTE_QUERY),
    RETURN_PENDING("退货待审核", OrderPermission.RETURN_QUERY),
    REFUND_PENDING("退款单待完成", OrderPermission.REFUND_QUERY),
    RETURN_REJECTED("售后拒绝", OrderPermission.RETURN_QUERY);

    private final String label;
    private final String queryPermission;
}
