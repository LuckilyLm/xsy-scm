package net.lab1024.sa.admin.module.scm.finance.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 应收明细：正常一条 {@code inventory_outbound_item} 一行，红字一条 {@code order_return_item} 一行。
 *
 * <p><b>来源锚点是出库行主键，不是 {@code sales_order_item_id}</b>：V63 刻意不给出库行建
 * {@code (sales_order_item_id)} 唯一索引（一条订单行将来可能被再出一行），所以行级唯一只能挂在
 * {@code inventory_outbound_item.id} 上，由 {@code uk_finance_receivable_item_source_active} 承担。
 *
 * <p><b>红字明细不存行级原明细指针</b>（设计稿 §3.2）：一条 {@code sales_order_item} 可能对应多条
 * 出库行，不存在唯一的「原正常明细行」，假设 1:1 会造出一个指错行的外键语义。红字的行级追溯链是
 * {@code RED receivable → original_receivable_id（单头级）} +
 * {@code RED item → source(order_return_item) → orderItemId}。
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
     * 正常 = 实际出库量（Q2，唯一数量来源）；红字 = 退货批准量。恒 &gt; 0。
     */
    private BigDecimal quantity;

    /**
     * 正常 = {@code sales_order_item.locked_unit_price}（Q3，唯一价格源）；红字 = 退货行锁定单价。
     * <b>禁止</b>取 {@code inventory_movement.unit_cost} —— 那是出库时的库存移动加权均价，不是售价。
     */
    private BigDecimal unitPrice;

    /**
     * 正常 = {@code ROUND(quantity × unitPrice, 4)}；红字 = {@code order_return_item.approved_amount}
     * （订单域已算，财务不重算、不改写，D-4）。
     */
    private BigDecimal amount;
}
