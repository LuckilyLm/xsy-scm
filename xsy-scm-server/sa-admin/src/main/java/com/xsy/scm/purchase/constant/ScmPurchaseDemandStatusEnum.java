package com.xsy.scm.purchase.constant;

/**
 * 采购需求状态（3 值）。
 *
 * <p>取值与 {@code ck_purchase_demand_status} 的白名单一致。
 *
 * <p>判据完全由 {@code allocated_quantity} 与 {@code required_quantity} 决定：
 * {@code PENDING} = 0 · {@code PARTIALLY_ALLOCATED} = 0 &lt; allocated &lt; required ·
 * {@code ALLOCATED} = allocated == required。
 */
public enum ScmPurchaseDemandStatusEnum {PENDING, PARTIALLY_ALLOCATED, ALLOCATED}
