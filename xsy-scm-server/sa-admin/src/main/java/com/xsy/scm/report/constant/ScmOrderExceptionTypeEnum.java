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
    SORTING_DIFFERENCE("分拣数量差异", SortingPermission.TASK_QUERY),
    DELIVERY_EXCEPTION("配送异常签收", DeliveryPermission.ROUTE_QUERY),
    RETURN_REJECTED("售后拒绝", OrderPermission.RETURN_QUERY);

    private final String label;
    private final String queryPermission;
}
