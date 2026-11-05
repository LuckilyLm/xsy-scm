package com.xsy.scm.purchase.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 采购防重复处理结果使用的资源类型。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPurchaseIdempotencyResourceTypeEnum {
    PURCHASE_ORDER("采购单"),
    PURCHASE_ACCOUNT("采购财务确认"),
    PURCHASE_COST_ADJUSTMENT("采购成本调整");

    private final String desc;
}
