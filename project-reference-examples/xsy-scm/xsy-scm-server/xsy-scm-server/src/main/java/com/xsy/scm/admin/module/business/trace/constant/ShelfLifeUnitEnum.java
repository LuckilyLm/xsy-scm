package com.xsy.scm.admin.module.business.trace.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 保质期单位
 *
 * <p>对标蔬东坡 17.4：保质期可选按天或按月，按月按自然月计算。</p>
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum ShelfLifeUnitEnum implements BaseEnum {

    /**
     * 1 天
     */
    DAY(1, "天"),

    /**
     * 2 月
     */
    MONTH(2, "月"),

    ;

    private final Integer value;

    private final String desc;
}
