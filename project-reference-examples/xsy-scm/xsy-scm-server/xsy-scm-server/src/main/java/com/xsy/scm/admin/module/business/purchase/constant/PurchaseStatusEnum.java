package com.xsy.scm.admin.module.business.purchase.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 采购单状态
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum PurchaseStatusEnum implements BaseEnum {

    /**
     * 1 待接单
     */
    PENDING(1, "待接单"),

    /**
     * 2 采购中
     */
    PURCHASING(2, "采购中"),

    /**
     * 3 部分收货
     */
    PARTIAL_RECEIVED(3, "部分收货"),

    /**
     * 4 已完成
     */
    COMPLETED(4, "已完成"),

    /**
     * 5 已取消
     */
    CANCELLED(5, "已取消"),

    ;

    private final Integer value;

    private final String desc;
}
