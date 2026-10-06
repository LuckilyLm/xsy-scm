package com.xsy.scm.finance.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 红字应收所需的<b>退货批准事实</b>（明细维度）：一条 {@code order_return_item} 一行红字明细。
 *
 * <p>
 * {@code amount} 直接取订单域已落库的 {@code approved_amount} （{@code OrderReturnService.approve} 按
 * {@code round(approved_quantity × locked_unit_price, 4)} 算好），财务<b>不重算</b> —— 重算就是给同一个金额造第二个权威来源。
 */
@Data
public class FinanceReturnSourceLineDto {

    private Long orderReturnItemId;

    private Long orderItemId;

    /**
     * {@code order_return_item} 不存商品快照，SKU 三件套从其来源订单行取。
     */
    private Long skuId;

    private String skuNameSnapshot;

    private String unitSnapshot;

    private BigDecimal approvedQuantity;

    private BigDecimal lockedUnitPrice;

    /**
     * 订单域已算好的批准金额，财务原样采用。
     */
    private BigDecimal approvedAmount;

    /**
     * 订单行的下单金额 {@code sales_order_item.ordered_line_amount}（下单量 × 锁定单价）。
     *
     * <p>
     * 与正常应收同一把尺子：红字要反向的优惠 = 冻结行分摊 × 本次退货金额 / 下单金额。 该式与「已确认优惠 × 退货金额 / 已出库金额」恒等（优惠本就按下单金额等比冻结）， 因此不必回读正常应收明细就能得到一致的反向额。
     */
    private BigDecimal orderedLineAmount;
}
