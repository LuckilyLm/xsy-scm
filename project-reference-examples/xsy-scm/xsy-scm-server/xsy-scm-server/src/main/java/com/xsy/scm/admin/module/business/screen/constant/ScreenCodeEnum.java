package com.xsy.scm.admin.module.business.screen.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 数据大屏 编码
 *
 * <p>经营 / 库存 / 采购大屏基于现有表可实时聚合；配送 / 溯源 / 分拣大屏待对应模块落地后接入。</p>
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum ScreenCodeEnum implements BaseEnum {

    /**
     * 1 经营大屏
     */
    BUSINESS(1, "经营大屏"),

    /**
     * 2 库存大屏
     */
    STOCK(2, "库存大屏"),

    /**
     * 3 采购大屏
     */
    PURCHASE(3, "采购大屏"),

    /**
     * 4 分拣绩效大屏（待分拣模块落地）
     */
    SORT(4, "分拣绩效大屏"),

    /**
     * 5 配送大屏（待配送模块落地）
     */
    DELIVERY(5, "配送大屏"),

    /**
     * 6 溯源大屏（待溯源模块落地）
     */
    TRACE(6, "溯源大屏"),

    ;

    private final Integer value;

    private final String desc;
}
