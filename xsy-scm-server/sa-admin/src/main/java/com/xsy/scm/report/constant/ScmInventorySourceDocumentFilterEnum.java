package com.xsy.scm.report.constant;

/** Source document types accepted by the inventory movement report filter. */
public enum ScmInventorySourceDocumentFilterEnum {
    PURCHASE_RECEIPT_ITEM,
    SALES_OUTBOUND_ITEM,
    STOCKTAKE_ITEM,
    LOSS_GAIN_ITEM,
    TRANSFER_OUT_ITEM,
    TRANSFER_IN_ITEM,
    CONVERT_OUT_ITEM,
    CONVERT_IN_ITEM
}
