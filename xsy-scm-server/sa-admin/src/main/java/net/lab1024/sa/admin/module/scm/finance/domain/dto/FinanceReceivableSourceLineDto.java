package net.lab1024.sa.admin.module.scm.finance.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 应收生成所需的**签收事实**（明细维度）：一条 {@code inventory_outbound_item} 一行应收明细。
 *
 * <p>量与价刻意来自两张表：出库行是「实际发了多少」的唯一事实（第一批 Q2），
 * 订单行是「按什么价格结算」的唯一事实（第一批 Q3）。两者都不允许被订单结算列或库存成本列替代。
 */
@Data
public class FinanceReceivableSourceLineDto {

    private Long inventoryOutboundItemId;

    /**
     * 出库行上的来源订单行 id（V63）；一条订单行将来可能对应多条出库行，
     * 因此它只是行级追溯列，不是唯一键。
     */
    private Long salesOrderItemId;

    private Long skuId;

    private String skuNameSnapshot;

    /**
     * 单价所在单位的快照，取订单行的 {@code sale_unit_snapshot}：出库行的 {@code unit_snapshot}
     * 由发车链路留空，而 {@code locked_unit_price} 正是按订单行的销售单位计价的。
     */
    private String unitSnapshot;

    /**
     * 实际出库量 {@code inventory_outbound_item.quantity}，库级 CHECK 恒 &gt; 0。
     */
    private BigDecimal quantity;

    /**
     * 冻结售价 {@code sales_order_item.locked_unit_price}，允许合法为 0（赠品 / 0 元单）。
     */
    private BigDecimal unitPrice;
}
