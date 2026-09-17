package com.xsy.scm.admin.module.business.customer.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 客户商品可见性
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum VisibleTypeEnum implements BaseEnum {

    /**
     * 1 显示
     */
    VISIBLE(1, "显示"),

    /**
     * 2 屏蔽
     */
    HIDDEN(2, "屏蔽"),

    ;

    private final Integer value;

    private final String desc;
}
