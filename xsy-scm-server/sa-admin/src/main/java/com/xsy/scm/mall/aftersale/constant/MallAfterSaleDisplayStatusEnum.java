package com.xsy.scm.mall.aftersale.constant;

import com.xsy.scm.order.constant.ScmOrderRefundStatusEnum;
import com.xsy.scm.order.constant.ScmOrderReturnStatusEnum;

/**
 * 商城售后单的展示分类：由正式退货单状态与退款单状态派生，不新增状态机。
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
     * @param returnStatus 正式退货单状态
     * @param refundStatus 退款单状态；该退货单没有退款单（净额为 0）时为 null
     */
    public static MallAfterSaleDisplayStatusEnum derive(String returnStatus, String refundStatus) {
        if (ScmOrderReturnStatusEnum.REJECTED.name().equals(returnStatus)) {
            return REJECTED;
        }
        if (ScmOrderReturnStatusEnum.CANCELLED.name().equals(returnStatus)) {
            return CANCELLED;
        }
        if (ScmOrderReturnStatusEnum.APPROVED.name().equals(returnStatus)) {
            return refundStatus == null || ScmOrderRefundStatusEnum.COMPLETED.name().equals(refundStatus) ? REFUNDED
                    : REFUNDING;
        }
        return REVIEWING;
    }
}
