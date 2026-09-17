package com.xsy.scm.admin.module.business.order.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单来源
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum OrderSourceEnum implements BaseEnum {

    /**
     * 1 商城下单
     */
    MALL(1, "商城下单"),

    /**
     * 2 后台录单
     */
    ADMIN(2, "后台录单"),

    /**
     * 3 补单
     */
    SUPPLEMENT(3, "补单"),

    ;

    private final Integer value;

    private final String desc;
}
