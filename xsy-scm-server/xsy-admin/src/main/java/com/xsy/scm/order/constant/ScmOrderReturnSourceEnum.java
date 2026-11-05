package com.xsy.scm.order.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 退货单是否关联原销售订单。
 */
@Getter
@RequiredArgsConstructor
public enum ScmOrderReturnSourceEnum {

    /**
     * 从原订单明细发起，数量受原订单实发量限制。
     */
    ORDER("原单退货"),

    /**
     * 客户直接退回、找不到原单：明细按 SKU 建行，数量不受原订单限制。
     */
    INDEPENDENT("独立退货");

    private final String desc;
}
