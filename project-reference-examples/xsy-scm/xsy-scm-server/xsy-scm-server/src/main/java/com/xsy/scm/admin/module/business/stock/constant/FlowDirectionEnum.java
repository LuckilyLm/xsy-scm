package com.xsy.scm.admin.module.business.stock.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 库存流水方向
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum FlowDirectionEnum implements BaseEnum {

    /**
     * 1 入
     */
    IN(1, "入"),

    /**
     * 2 出
     */
    OUT(2, "出"),

    ;

    private final Integer value;

    private final String desc;
}
