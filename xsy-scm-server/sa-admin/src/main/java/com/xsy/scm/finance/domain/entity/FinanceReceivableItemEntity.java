package com.xsy.scm.finance.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 正常应收按出库明细生成，退货红字按已批准退货明细生成。来源身份独立防重，金额按原订单的冻结结算价与优惠分摊处理，不按当前商品价格重新计算。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("xsy_finance_receivable_item")
public class FinanceReceivableItemEntity extends FinanceRecord {

    private Long receivableId;

    /**
     * {@code ScmFinanceReceivableItemSourceTypeEnum}。
     */
    private String sourceType;

    /**
     * 正常 = {@code xsy_inventory_outbound_item.id}；红字 = {@code xsy_order_return_item.id}。
     */
    private Long sourceId;

    /**
     * 销售订单行 id，行级追溯与按订单行聚合展示用。
     */
    private Long orderItemId;

    private Long skuId;

    private String skuNameSnapshot;

    /**
     * 单位名称快照；不做单位换算，也不跨单位求和。
     */
    private String unitSnapshot;

    /**
     * 正常 = 实际出库量（唯一数量来源）；红字 = 退货批准量。恒 &gt; 0。
     */
    private BigDecimal quantity;

    /**
     * 正常 = {@code xsy_sales_order_item.locked_unit_price}（唯一价格源）；红字 = 退货行锁定单价。 <b>禁止</b>取
     * {@code xsy_inventory_movement.unit_cost} —— 那是出库时的库存移动加权均价，不是售价。
     */
    private BigDecimal unitPrice;

    /** 应收明细的优惠额来自订单确认时冻结的分配，正常与红字均按同一口径处理，不能用现行活动重新折算。 */
    private BigDecimal discountAmount;

    /**
     * 行净额 = 毛额 − {@link #discountAmount}（库上有 {@code amount >= 0}，减多了会被拒）。
     *
     * <p>
     * 正常 = {@code ROUND(quantity × unitPrice, 4)} 再扣优惠；红字 = 订单域已算的 {@code approved_amount} 再扣反向优惠。
     */
    private BigDecimal amount;

    /**
     * 行毛额 = {@link #amount} + {@link #discountAmount}。
     *
     * <p>
     * <b>刻意不落库</b>：毛额可由净额与优惠精确还原，多存一列派生态就会与既有 「不落余额 / 结清状态列」的纪律冲突，也会给「三列互相漂移」留出空间。
     */
    public BigDecimal getGrossAmount() {
        if (amount == null) {
            return null;
        }
        return discountAmount == null ? amount : amount.add(discountAmount);
    }
}
