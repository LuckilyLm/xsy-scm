package com.xsy.scm.mall.order.constant;

/**
 * 商城进度时间线的节点编码：订单与售后共用同一套，客户端按它渲染节点图标与文案。
 *
 * <p>
 * 这是给客户端的稳定契约，与业务状态机无关 —— 节点是否点亮只由「对应正式事实是否存在」决定， 不推导状态、不预测下一步。
 */
public enum MallOrderProgressNodeEnum {

    /**
     * 已提交。
     */
    SUBMITTED,

    /**
     * 平台审核。
     */
    REVIEW,

    /**
     * 申请被驳回。
     */
    REJECTED,

    /**
     * 订单已取消 / 售后已撤销。
     */
    CANCELLED,

    /**
     * 后台已接单。
     */
    CONFIRMED,

    /**
     * 已发车配送。
     */
    DISPATCHED,

    /**
     * 已签收，含异常签收。
     */
    SIGNED,

    /**
     * 退款处理，含无需退款。
     */
    REFUND
}
