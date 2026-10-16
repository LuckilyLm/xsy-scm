package com.xsy.scm.finance.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 红字应收明细使用退货审批时已经保存的批准金额，不在财务模块用当前价格重新计算。 */
@Data
public class FinanceReturnSourceLineDto {

    private Long orderReturnItemId;

    private Long orderItemId;

    /**
     * {@code xsy_order_return_item} 不存商品快照，SKU 三件套从其来源订单行取。
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
     * 订单行的下单金额 {@code xsy_sales_order_item.ordered_line_amount}（下单量 × 锁定单价）。
     *
     * <p>
     * 与正常应收同一把尺子：红字要反向的优惠 = 冻结行分摊 × 本次退货金额 / 下单金额。 该式与「已确认优惠 × 退货金额 / 已出库金额」恒等（优惠本就按下单金额等比冻结）， 因此不必回读正常应收明细就能得到一致的反向额。
     */
    private BigDecimal orderedLineAmount;
}
