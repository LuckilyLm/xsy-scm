package com.xsy.scm.mall.order.constant;

import com.xsy.scm.order.constant.ScmOrderStatusEnum;

/**
 * 商城页面分类直接映射订单当前状态，取消仍由订单状态机控制。
 */
public enum MallOrderDisplayStatusEnum {
    /**
     * 待处理：订单已新建但后台尚未确认。
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
     * 由订单当前状态映射展示分类。
     *
     * @param orderStatus
     *            销售订单状态；未知值按「待处理」兜底，不抛异常以免列表整页失败
     * @param fulfillmentStatus
     *            保留调用兼容，不参与当前状态判定
     */
    public static MallOrderDisplayStatusEnum derive(String orderStatus, String fulfillmentStatus) {
        if (ScmOrderStatusEnum.CANCELLED.name().equals(orderStatus))
            return CANCELLED;
        if (ScmOrderStatusEnum.CONFIRMED.name().equals(orderStatus))
            return PREPARING;
        if (ScmOrderStatusEnum.DISPATCHED.name().equals(orderStatus))
            return DELIVERING;
        if (ScmOrderStatusEnum.SIGNED.name().equals(orderStatus)
            || ScmOrderStatusEnum.EXCEPTION.name().equals(orderStatus))
            return COMPLETED;
        return PENDING;
    }

    /**
     * 该分类下客户是否可能取消。仅用于前端按钮置灰，服务端仍以订单状态机为准。
     */
    public boolean cancelable() {
        return this == PENDING;
    }
}
