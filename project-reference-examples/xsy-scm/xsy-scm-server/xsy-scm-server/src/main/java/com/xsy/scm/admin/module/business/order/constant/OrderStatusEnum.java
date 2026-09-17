package com.xsy.scm.admin.module.business.order.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单状态
 *
 * <p>终态：已取消(11)、已作废(12)。已签收(8) 时生成应收（09-01 已定）。</p>
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum OrderStatusEnum implements BaseEnum {

    /**
     * 1 草稿
     */
    DRAFT(1, "草稿"),

    /**
     * 2 待确认
     */
    PENDING(2, "待确认"),

    /**
     * 3 已确认
     */
    CONFIRMED(3, "已确认"),

    /**
     * 4 采购中
     */
    PURCHASING(4, "采购中"),

    /**
     * 5 待分拣
     */
    SORTING(5, "待分拣"),

    /**
     * 6 分拣中
     */
    SORTED(6, "分拣中"),

    /**
     * 7 配送中
     */
    DELIVERING(7, "配送中"),

    /**
     * 8 已签收
     */
    SIGNED(8, "已签收"),

    /**
     * 9 已完成
     */
    COMPLETED(9, "已完成"),

    /**
     * 10 退款中
     */
    REFUNDING(10, "退款中"),

    /**
     * 11 已取消
     */
    CANCELLED(11, "已取消"),

    /**
     * 12 已作废
     */
    INVALID(12, "已作废"),

    ;

    private final Integer value;

    private final String desc;
}
