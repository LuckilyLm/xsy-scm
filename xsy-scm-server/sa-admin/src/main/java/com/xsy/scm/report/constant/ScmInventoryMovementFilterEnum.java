package com.xsy.scm.report.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 库存流水报表可筛选的变动类型。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmInventoryMovementFilterEnum {

    /**
     * 采购入库。
     */
    PURCHASE_IN("采购入库"),

    /**
     * 销售出库。
     */
    SALES_OUT("销售出库"),

    /**
     * 盘盈。
     */
    STOCKTAKE_GAIN("盘盈"),

    /**
     * 盘亏。
     */
    STOCKTAKE_LOSS("盘亏"),

    /**
     * 报损。
     */
    LOSS_REPORT("报损"),

    /**
     * 报溢。
     */
    GAIN_REPORT("报溢"),

    /**
     * 调拨出库。
     */
    TRANSFER_OUT("调拨出库"),

    /**
     * 调拨入库。
     */
    TRANSFER_IN("调拨入库"),

    /**
     * 规格转换出库。
     */
    CONVERT_OUT("转换出库"),

    /**
     * 规格转换入库。
     */
    CONVERT_IN("转换入库"),

    /**
     * 促销赠品出库：赠品成本要能从流水单独筛出来。
     */
    PROMOTION_GIFT_OUT("赠品出库");

    private final String desc;
}
