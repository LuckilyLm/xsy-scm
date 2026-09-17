package com.xsy.scm.admin.module.business.product.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 计量方式
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum MeasureTypeEnum implements BaseEnum {

    /**
     * 1 按件
     */
    BY_QUANTITY(1, "按件"),

    /**
     * 2 按重
     */
    BY_WEIGHT(2, "按重"),

    ;

    private final Integer value;

    private final String desc;
}
