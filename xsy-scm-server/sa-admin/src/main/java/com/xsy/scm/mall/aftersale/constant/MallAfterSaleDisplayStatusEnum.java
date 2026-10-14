package com.xsy.scm.mall.aftersale.constant;

import com.xsy.scm.order.constant.ScmOrderReturnStatusEnum;

/**
 * 商城售后单的展示分类：由正式退货单与实际退款资金事实派生，不新增状态机。
 */
public enum MallAfterSaleDisplayStatusEnum {
    /** 已提交，等待平台审核 */
    REVIEWING,
    /** 审核通过，退款处理中 */
    REFUNDING,
    /** 退款完成（含退款净额为 0、无需退款） */
    REFUNDED,
    /** 申请被驳回 */
    REJECTED,
    /** 客户自行撤销 */
    CANCELLED;

    /**
     * @param returnStatus
     *            正式退货单状态
     * @param hasRefund
     *            该退货单是否有应退金额的退款单
     * @param funded
     *            退款资金是否实际完成
     */
    public static MallAfterSaleDisplayStatusEnum derive(String returnStatus, boolean hasRefund, boolean funded) {
        if (ScmOrderReturnStatusEnum.REJECTED.name().equals(returnStatus)) {
            return REJECTED;
        }
        if (ScmOrderReturnStatusEnum.CANCELLED.name().equals(returnStatus)) {
            return CANCELLED;
        }
        if (ScmOrderReturnStatusEnum.APPROVED.name().equals(returnStatus)) {
            return !hasRefund || funded ? REFUNDED : REFUNDING;
        }
        return REVIEWING;
    }
}
