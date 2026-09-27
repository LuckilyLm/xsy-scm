package com.xsy.scm.purchase.constant;

/**
 * 采购收货单状态（2 值）。
 *
 * <p>
 * 取值与 {@code ck_purchase_receipt_status} 的白名单一致。 {@code CONFIRMED} 表示本收货单已提交，不由采购单的整体收货进度决定；一张采购单可以对应多张收货单。
 */
public enum ScmReceiptStatusEnum {
    DRAFT,
    CONFIRMED
}
