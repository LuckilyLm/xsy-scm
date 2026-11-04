package com.xsy.scm.order.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 销售订单状态，与订单表的 CHECK 约束逐值对应。
 *
 * <p>
 * {@code desc} 供页面、导出与打印使用，保存仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmOrderStatusEnum {

    /**
     * 待确认：新建即进入此状态，等待仓库确认；确认前可修改或删除。
     */
    PENDING("待确认"),

    /**
     * 已确认：进入分拣与出库流程。
     */
    CONFIRMED("已确认"),

    DISPATCHED("已发车"),
    SIGNED("已签收"),
    EXCEPTION("签收异常"),

    /**
     * 已取消：不再参与分拣与配送。
     */
    CANCELLED("已取消");

    private final String desc;

    /** 已确认的交易，包括后续配送和签收阶段；用于历史价格、退货、资金与统计读取。 */
    public static boolean isConfirmed(String status) {
        return CONFIRMED.name().equals(status) || DISPATCHED.name().equals(status) || SIGNED.name().equals(status)
            || EXCEPTION.name().equals(status);
    }
}
