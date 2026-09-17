package com.xsy.scm.admin.module.business.stock.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 库存流水类型
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum StockFlowTypeEnum implements BaseEnum {

    /**
     * 1 采购入库
     */
    PURCHASE_IN(1, "采购入库"),

    /**
     * 2 销售出库
     */
    SALE_OUT(2, "销售出库"),

    /**
     * 3 退货入库
     */
    RETURN_IN(3, "退货入库"),

    /**
     * 4 报损
     */
    LOSS(4, "报损"),

    /**
     * 5 报溢
     */
    OVERFLOW(5, "报溢"),

    /**
     * 6 盘点调整
     */
    CHECK_ADJUST(6, "盘点调整"),

    /**
     * 7 规格转换出
     */
    CONVERT_OUT(7, "规格转换出"),

    /**
     * 8 规格转换入
     */
    CONVERT_IN(8, "规格转换入"),

    ;

    private final Integer value;

    private final String desc;
}
