package com.xsy.scm.admin.module.business.purchase.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 收货标记（Q2 动态记录少收 / 超收）
 *
 * <p>每次收货按「累计已收 vs 订单需求量」打标：小于记少收、等于记正常、大于记超收。</p>
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum ReceiveFlagEnum implements BaseEnum {

    /**
     * 1 正常（累计恰好收齐）
     */
    NORMAL(1, "正常"),

    /**
     * 2 少收（累计小于订单需求量）
     */
    UNDER(2, "少收"),

    /**
     * 3 超收（累计大于订单需求量）
     */
    OVER(3, "超收"),

    ;

    private final Integer value;

    private final String desc;
}
