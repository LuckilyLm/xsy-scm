package com.xsy.scm.delivery.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 订单级配送履约状态，与 {@code delivery_route_order} 的 CHECK 约束一致。
 *
 * <p>
 * {@code desc} 供页面与导出使用，保存仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmDeliveryFulfillmentStatusEnum {

    /** 待配送：已排线，尚未发车。 */
    PENDING("待配送"),

    /** 配送中：已发车在途。 */
    IN_TRANSIT("配送中"),

    /** 已签收：客户确认收货。 */
    SIGNED("已签收"),

    /** 异常：签收异常，需人工跟进。 */
    EXCEPTION("异常");

    private final String desc;
}
