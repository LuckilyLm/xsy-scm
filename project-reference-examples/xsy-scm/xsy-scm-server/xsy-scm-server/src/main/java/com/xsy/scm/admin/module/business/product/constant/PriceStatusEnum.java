package com.xsy.scm.admin.module.business.product.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 价格状态
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum PriceStatusEnum implements BaseEnum {

    /**
     * 1 生效
     */
    EFFECTIVE(1, "生效"),

    /**
     * 2 失效
     */
    EXPIRED(2, "失效"),

    ;

    private final Integer value;

    private final String desc;
}
