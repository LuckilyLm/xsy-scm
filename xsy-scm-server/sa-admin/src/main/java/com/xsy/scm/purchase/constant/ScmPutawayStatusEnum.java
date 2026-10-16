package com.xsy.scm.purchase.constant;

/** 收货确认与库存入账是两种状态。直接入库模式在收货确认时完成入账；仓库二次确认模式须等仓库确认后才变成已入库。 */
public enum ScmPutawayStatusEnum {
    PENDING,
    COMPLETED
}
