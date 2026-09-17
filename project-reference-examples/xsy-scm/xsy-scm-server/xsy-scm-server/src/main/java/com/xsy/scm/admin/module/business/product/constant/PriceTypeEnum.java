package com.xsy.scm.admin.module.business.product.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 价格类型
 *
 * <p>取价优先级（01-01 已定）：协议价 > 促销价 > 分级价 > 时价 > 基础价，不叠加</p>
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum PriceTypeEnum implements BaseEnum {

    /**
     * 1 基础价
     */
    BASE(1, "基础价"),

    /**
     * 2 客户分级价
     */
    LEVEL(2, "客户分级价"),

    /**
     * 3 时价
     */
    MARKET(3, "时价"),

    /**
     * 4 协议价
     */
    AGREEMENT(4, "协议价"),

    ;

    private final Integer value;

    private final String desc;
}
