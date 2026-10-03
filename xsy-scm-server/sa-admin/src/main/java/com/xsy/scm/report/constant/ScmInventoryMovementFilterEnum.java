package com.xsy.scm.report.constant;

/** Movement types accepted by the inventory movement report filter. */
public enum ScmInventoryMovementFilterEnum {
    PURCHASE_IN,
    SALES_OUT,
    STOCKTAKE_GAIN,
    STOCKTAKE_LOSS,
    LOSS_REPORT,
    GAIN_REPORT,
    TRANSFER_OUT,
    TRANSFER_IN,
    CONVERT_OUT,
    CONVERT_IN,
    /** 促销赠品出库（ADM-12 3-5b）：赠品成本要能从流水单独筛出来。 */
    PROMOTION_GIFT_OUT
}
