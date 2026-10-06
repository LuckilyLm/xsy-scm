package com.xsy.scm.finance.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 应付生成所需的<b>收货确认事实</b>（明细维度）：一行收货 = 一行应付明细。
 *
 * <p>
 * 量取收货行、价取采购行，两者刻意来自不同的表：收货行不存价格，采购行的 {@code purchase_price} 是这批货结算时用过的单价（不含任何税务语义）。
 */
@Data
public class FinancePayableSourceLineDto {

    private Long purchaseReceiptItemId;

    private Long purchaseOrderItemId;

    private Long skuId;

    private String skuNameSnapshot;

    /**
     * 数量所在的单位：与 {@link #quantity} 同一行的 {@code purchase_unit_snapshot}，不做单位换算，也不跨单位求和。
     */
    private String unitSnapshot;

    /**
     * 本次收货的有效量：标品取申报量、非标品取实重（{@code PurchaseReceiptQuantityCalculator}），含合法容差内的超收。SQL 已过滤为 &gt; 0。
     */
    private BigDecimal quantity;

    /**
     * 采购结算单价 {@code purchase_order_item.purchase_price}。
     */
    private BigDecimal unitPrice;
}
