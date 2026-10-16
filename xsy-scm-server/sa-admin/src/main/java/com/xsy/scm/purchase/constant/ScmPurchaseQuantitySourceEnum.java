package com.xsy.scm.purchase.constant;

/** 标准品按申报数量入库，非标品按实际称重入库。数量来源是系统或人工，与称重设备来源是不同概念。 */
public enum ScmPurchaseQuantitySourceEnum {
    SYSTEM,
    MANUAL
}
