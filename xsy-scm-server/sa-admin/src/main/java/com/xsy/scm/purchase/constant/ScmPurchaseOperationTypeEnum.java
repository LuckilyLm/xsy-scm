package com.xsy.scm.purchase.constant;

/** 采购操作类型与数据库白名单保持一致。生成需求、需求分配和收货操作的归属字段不同，必须按操作类型写入并由数据库约束校验。 */
public enum ScmPurchaseOperationTypeEnum {

    // ---- 采购单 ----
    CREATE,
    UPDATE,
    SUBMIT,
    CANCEL,
    SHORT_CLOSE,
    DELETE,

    // ---- 采购需求（无采购单 / 收货单可挂）----
    DEMAND_GENERATE,
    DEMAND_ALLOCATE,

    // ---- 采购收货（必属采购单）----
    RECEIPT_CREATE,
    RECEIPT_UPDATE,
    RECEIPT_CONFIRM,
    RECEIPT_DELETE,

    // ---- 入库确认（必须关联采购单与收货单）----
    RECEIPT_PUTAWAY
}
