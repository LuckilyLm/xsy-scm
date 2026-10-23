package com.xsy.scm.delivery.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 配送线路状态，与 {@code delivery_route} 的 CHECK 约束一致。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmDeliveryRouteStatusEnum {

    /**
     * 草稿：线路尚在编辑。
     */
    DRAFT("草稿"),

    /**
     * 已排线：订单已挂到线路上，未发车。
     */
    PLANNED("已排线"),

    /**
     * 已发车：出库事实已产生。
     */
    DISPATCHED("已发车"),

    /**
     * 已完成：线路内订单全部签收。
     */
    COMPLETED("已完成"),

    /**
     * 已取消：不再配送。
     */
    CANCELLED("已取消");

    private final String desc;
}
