package com.xsy.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 正常应收明细以出库行 ID 为来源，红字明细以退货行 ID 为来源。订单行可能多次出库，不能假设一条订单行只对应一条应收明细。
 */
@Getter
@RequiredArgsConstructor
public enum ScmFinanceReceivableItemSourceTypeEnum {

    /**
     * 正常明细：{@code source_id} = {@code xsy_inventory_outbound_item.id}。
     */
    INVENTORY_OUTBOUND_ITEM("库存出库明细"),

    /**
     * 红字明细：{@code source_id} = {@code xsy_order_return_item.id}。
     */
    ORDER_RETURN_ITEM("销售退货明细");

    private final String desc;
}
