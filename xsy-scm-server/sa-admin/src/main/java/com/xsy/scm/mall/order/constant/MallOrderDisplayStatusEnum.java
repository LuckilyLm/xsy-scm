package com.xsy.scm.mall.order.constant;

import com.xsy.scm.delivery.constant.ScmDeliveryFulfillmentStatusEnum;
import com.xsy.scm.order.constant.ScmOrderStatusEnum;

/**
 * 商城订单阶段由订单状态和配送进度合成，只用于页面展示，不落库也不改变订单状态机；能否取消仍由订单规则决定。
 */
public enum MallOrderDisplayStatusEnum {
    /**
     * 待处理：订单已提交但后台尚未确认（含草稿）。
     */
    PENDING,
    /**
     * 备货中：订单已确认，尚未发车。
     */
    PREPARING,
    /**
     * 配送中：已发车在途。
     */
    DELIVERING,
    /**
     * 已完成：已签收，含异常签收（异常原因在详情展示）。
     */
    COMPLETED,
    /**
     * 已取消。
     */
    CANCELLED;

    /**
     * 由订单状态与履约状态派生展示分类。
     *
     * @param orderStatus
     *            销售订单状态；未知值按「待处理」兜底，不抛异常以免列表整页失败
     * @param fulfillmentStatus
     *            生效线路订单的履约状态；无线路订单时传 {@code null}
     */
    public static MallOrderDisplayStatusEnum derive(String orderStatus, String fulfillmentStatus) {
        if (ScmOrderStatusEnum.CANCELLED.name().equals(orderStatus)) {
            return CANCELLED;
        }
        if (ScmOrderStatusEnum.DRAFT.name().equals(orderStatus)
            || ScmOrderStatusEnum.PENDING.name().equals(orderStatus)) {
            return PENDING;
        }
        if (!ScmOrderStatusEnum.CONFIRMED.name().equals(orderStatus)) {
            // 未知状态不猜：宁可显示「待处理」让客户去问，也不要谎称已备货或已完成
            return PENDING;
        }
        // 走到这里必然是 CONFIRMED。已确认但还没发车时归「备货中」而不是「待处理」：
        // 后台已接单，只是尚未排车，把它说成待处理会让客户以为没人管。
        if (ScmDeliveryFulfillmentStatusEnum.IN_TRANSIT.name().equals(fulfillmentStatus)) {
            return DELIVERING;
        }
        if (ScmDeliveryFulfillmentStatusEnum.SIGNED.name().equals(fulfillmentStatus)
            || ScmDeliveryFulfillmentStatusEnum.EXCEPTION.name().equals(fulfillmentStatus)) {
            return COMPLETED;
        }
        return PREPARING;
    }

    /**
     * 该分类下客户是否可能取消。仅用于前端按钮置灰，服务端仍以订单状态机为准。
     */
    public boolean cancelable() {
        return this == PENDING;
    }
}
