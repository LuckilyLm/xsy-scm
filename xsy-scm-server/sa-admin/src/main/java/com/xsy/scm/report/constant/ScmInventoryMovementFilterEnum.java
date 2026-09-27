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
    CONVERT_IN
}
