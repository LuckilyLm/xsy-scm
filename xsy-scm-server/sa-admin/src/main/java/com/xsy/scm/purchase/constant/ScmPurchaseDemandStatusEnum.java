package com.xsy.scm.purchase.constant;

/** 采购需求的待处理、部分分配和已分配状态由累计已分配数量决定，并与数据库白名单保持一致。 */
public enum ScmPurchaseDemandStatusEnum {
    PENDING,
    PARTIALLY_ALLOCATED,
    ALLOCATED
}
