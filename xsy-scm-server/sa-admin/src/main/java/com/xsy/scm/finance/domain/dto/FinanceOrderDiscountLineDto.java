package com.xsy.scm.finance.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单确认时冻结的<b>行级</b>优惠分摊，供应收按净额生成。
 *
 * <p>
 * 只读订单域已冻结的 {@code order_discount.allocations}，财务不重算优惠规则 —— 重算就是给同一个优惠额造第二个权威来源。
 */
@Data
public class FinanceOrderDiscountLineDto {

    /** 订单行主键，与出库行 / 退货行上的 {@code order_item_id} 对齐。 */
    private Long orderItemId;

    /** 该订单行承担的优惠额（正数，表示减免）。 */
    private BigDecimal discountAmount;
}
