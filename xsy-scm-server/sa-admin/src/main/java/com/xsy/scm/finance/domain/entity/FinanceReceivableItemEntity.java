package com.xsy.scm.finance.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 应收明细：正常一条 {@code inventory_outbound_item} 一行，红字一条 {@code order_return_item} 一行。
 *
 * <p>
 * <b>来源锚点是出库行主键，不是 {@code sales_order_item_id}</b>：一条订单行可能对应多条出库行， 因此唯一索引
 * {@code uk_finance_receivable_item_source_active} 按 {@code inventory_outbound_item.id} 去重。
 *
 * <p>
 * <b>红字明细不存行级原明细指针</b>：一条 {@code sales_order_item} 可能对应多条 出库行，不存在唯一的「原正常明细行」，假设 1:1 会造出一个指错行的外键语义。红字的行级追溯链是
 * {@code RED receivable → original_receivable_id（单头级）} + {@code RED item → order_return_item → orderItemId}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("finance_receivable_item")
public class FinanceReceivableItemEntity extends FinanceRecord {

    private Long receivableId;

    /**
     * {@code ScmFinanceReceivableItemSourceTypeEnum}。
     */
    private String sourceType;

    /**
     * 正常 = {@code inventory_outbound_item.id}；红字 = {@code order_return_item.id}。
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
     * 正常 = {@code sales_order_item.locked_unit_price}（唯一价格源）；红字 = 退货行锁定单价。 <b>禁止</b>取
     * {@code inventory_movement.unit_cost} —— 那是出库时的库存移动加权均价，不是售价。
     */
    private BigDecimal unitPrice;

    /**
     * 该行承担的订单优惠分摊（正常为减免额；红字为按同一份冻结分摊反向的减免额）。恒 &gt;= 0。
     *
     * <p>
     * 来源是订单确认时冻结的 {@code order_discount.allocations}，按「该行金额 / 订单行下单金额」等比折算；
     * <b>不用</b>本次操作时的当前活动重算 —— 否则双十一买的单在双十二退货会退成另一个数。
     */
    private BigDecimal discountAmount;

    /**
     * 行净额 = 毛额 − {@link #discountAmount}（库上有 {@code amount >= 0}，减多了会被拒）。
     *
     * <p>
     * 正常 = {@code ROUND(quantity × unitPrice, 4)} 再扣优惠；红字 = 订单域已算的
     * {@code approved_amount} 再扣反向优惠。
     */
    private BigDecimal amount;

    /**
     * 行毛额 = {@link #amount} + {@link #discountAmount}。
     *
     * <p>
     * <b>刻意不落库</b>：毛额可由净额与优惠精确还原，多存一列派生态就会与既有
     * 「不落余额 / 结清状态列」的纪律冲突，也会给「三列互相漂移」留出空间。
     */
    public BigDecimal getGrossAmount() {
        if (amount == null) {
            return null;
        }
        return discountAmount == null ? amount : amount.add(discountAmount);
    }
}
