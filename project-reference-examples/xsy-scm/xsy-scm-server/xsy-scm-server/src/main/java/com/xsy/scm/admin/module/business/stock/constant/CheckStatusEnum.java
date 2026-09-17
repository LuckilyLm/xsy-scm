package com.xsy.scm.admin.module.business.stock.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 盘点单状态
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum CheckStatusEnum implements BaseEnum {

    /**
     * 1 待盘点
     */
    PENDING(1, "待盘点"),

    /**
     * 2 盘点中
     */
    CHECKING(2, "盘点中"),

    /**
     * 3 已完成
     */
    COMPLETED(3, "已完成"),

    /**
     * 4 已取消
     */
    CANCELLED(4, "已取消"),

    ;

    private final Integer value;

    private final String desc;
}
