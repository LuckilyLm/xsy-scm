package com.xsy.scm.purchase.constant;

/**
 * 采购单状态（6 值）。
 *
 * <p>
 * 取值与 {@code ck_purchase_order_status} 的白名单一致。
 *
 * <p>
 * 终态为 {@code RECEIVED} / {@code SHORT_CLOSED} / {@code CANCELLED}； {@code PARTIALLY_RECEIVED} 不允许 cancel，需要终止时使用
 * {@code shortClose}。
 */
public enum ScmPurchaseStatusEnum {
    DRAFT, SUBMITTED, PARTIALLY_RECEIVED, RECEIVED, SHORT_CLOSED, CANCELLED
}
