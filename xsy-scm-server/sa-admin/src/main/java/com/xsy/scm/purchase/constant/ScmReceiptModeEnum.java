package com.xsy.scm.purchase.constant;

/** 直接入库在确认收货时记库存；仓库二次确认先只记收货事实，等待入库操作。不允许用收货完成状态代替入库状态。 */
public enum ScmReceiptModeEnum {
    DIRECT,
    WAREHOUSE_CONFIRM
}
