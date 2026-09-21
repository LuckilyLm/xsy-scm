package net.lab1024.sa.admin.module.scm.purchase.constant;

/**
 * 采购单状态（6 值）。
 *
 * <p>设计依据：W5 Target Design §4.1（Q2）。取值与 V15 的
 * {@code ck_purchase_order_status} 白名单逐字一致。
 *
 * <p>终态为 {@code RECEIVED} / {@code SHORT_CLOSED} / {@code CANCELLED}；
 * {@code PARTIALLY_RECEIVED} **不允许** cancel（P14），需要终止时使用 {@code shortClose}（Q2a）。
 */
public enum ScmPurchaseStatusEnum {DRAFT, SUBMITTED, PARTIALLY_RECEIVED, RECEIVED, SHORT_CLOSED, CANCELLED}
