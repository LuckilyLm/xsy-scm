package com.xsy.scm.admin.module.business.order.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单操作类型（订单操作日志）
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum OrderOperateTypeEnum implements BaseEnum {

    /**
     * 1 创建
     */
    CREATE(1, "创建"),

    /**
     * 2 确认
     */
    CONFIRM(2, "确认"),

    /**
     * 3 改价
     */
    CHANGE_PRICE(3, "改价"),

    /**
     * 4 编辑
     */
    EDIT(4, "编辑"),

    /**
     * 5 取消
     */
    CANCEL(5, "取消"),

    /**
     * 6 发货
     */
    DELIVER(6, "发货"),

    /**
     * 7 签收
     */
    SIGN(7, "签收"),

    /**
     * 8 核算
     */
    SETTLE(8, "核算"),

    /**
     * 9 退款
     */
    REFUND(9, "退款"),

    /**
     * 10 作废
     */
    INVALID(10, "作废"),

    ;

    private final Integer value;

    private final String desc;
}
