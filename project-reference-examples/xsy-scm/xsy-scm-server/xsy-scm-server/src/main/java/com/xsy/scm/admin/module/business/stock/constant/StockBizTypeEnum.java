package com.xsy.scm.admin.module.business.stock.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 库存流水关联业务类型
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum StockBizTypeEnum implements BaseEnum {

    /**
     * 1 采购
     */
    PURCHASE(1, "采购"),

    /**
     * 2 订单
     */
    ORDER(2, "订单"),

    /**
     * 3 分拣
     */
    SORT(3, "分拣"),

    /**
     * 4 盘点
     */
    CHECK(4, "盘点"),

    /**
     * 5 报损报溢
     */
    ADJUST(5, "报损报溢"),

    /**
     * 6 规格转换
     */
    CONVERT(6, "规格转换"),

    ;

    private final Integer value;

    private final String desc;
}
