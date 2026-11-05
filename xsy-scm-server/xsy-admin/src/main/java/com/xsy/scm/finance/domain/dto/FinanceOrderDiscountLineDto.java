package com.xsy.scm.finance.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单确认时保存的行级优惠分摊，应收按净额生成。
 *
 * <p>
 * 只读订单域已保存的 {@code xsy_order_discount.allocations}：财务不重算优惠规则，否则同一个优惠额会有两个来源。
 */
@Data
public class FinanceOrderDiscountLineDto {

    /**
     * 所属销售订单主键；批量取数时用来把行归回订单，单订单取数不使用。
     */
    private Long salesOrderId;

    /**
     * 订单行主键，与出库行 / 退货行上的 {@code order_item_id} 对齐。
     */
    private Long orderItemId;

    /**
     * 该订单行承担的优惠额（正数，表示减免）。
     */
    private BigDecimal discountAmount;
}
