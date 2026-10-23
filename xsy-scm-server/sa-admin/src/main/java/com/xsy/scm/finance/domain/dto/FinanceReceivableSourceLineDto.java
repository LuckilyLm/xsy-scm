package com.xsy.scm.finance.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 应收生成所需的签收数据（明细维度）：一条出库明细对应一行应收明细。
 *
 * <p>
 * 数量与价格来自两张表：出库行记录「实际发了多少」，订单行记录「按什么价格结算」，两者都不能用订单结算列或库存成本列替代。
 */
@Data
public class FinanceReceivableSourceLineDto {

    private Long inventoryOutboundItemId;

    /**
     * 出库行上的来源订单行 id；一条订单行将来可能对应多条出库行，因此它只是行级追溯列，不是唯一键。
     */
    private Long salesOrderItemId;

    private Long skuId;

    private String skuNameSnapshot;

    /**
     * 单价所在单位的快照，取订单行的 {@code sale_unit_snapshot}：出库行的 {@code unit_snapshot} 由发车链路留空，而 {@code locked_unit_price}
     * 正是按订单行的销售单位计价的。
     */
    private String unitSnapshot;

    /**
     * 实际出库量 {@code xsy_inventory_outbound_item.quantity}，库级 CHECK 恒 &gt; 0。
     */
    private BigDecimal quantity;

    /**
     * 冻结售价 {@code xsy_sales_order_item.locked_unit_price}，允许合法为 0（赠品 / 0 元单）。
     */
    private BigDecimal unitPrice;

    /**
     * 订单行的下单金额 {@code xsy_sales_order_item.ordered_line_amount}（下单量 × 锁定单价）。
     *
     * <p>
     * 只用来把订单确认时保存的行优惠分摊按比例折算到本次实际出库量上：优惠是按下单金额保存的， 少发时该行应承担的优惠也要按比例变小，不能整额落到部分出库行上。
     */
    private BigDecimal orderedLineAmount;
}
