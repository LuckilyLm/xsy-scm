package com.xsy.scm.admin.module.business.stock.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 库存调整类型
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum AdjustTypeEnum implements BaseEnum {

    /**
     * 1 报损
     */
    LOSS(1, "报损"),

    /**
     * 2 报溢
     */
    OVERFLOW(2, "报溢"),

    /**
     * 3 盘点调整
     */
    CHECK_ADJUST(3, "盘点调整"),

    /**
     * 4 规格转换
     */
    CONVERT(4, "规格转换"),

    ;

    private final Integer value;

    private final String desc;
}
