package com.xsy.scm.finance.domain.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 已确认订单的应付行数据：数量取实重，价格取保存的售价，与签收应收算法一致。
 */
@Data
public class FinanceOrderPayableLineDto {

    /**
     * 所属销售订单主键；批量取数时用来把行归回订单，单订单取数不使用。
     */
    private Long salesOrderId;

    /**
     * 订单行主键，与冻结优惠分摊上的 {@code orderItemId} 对齐。
     */
    private Long orderItemId;

    /**
     * 实重数量。
     *
     * <p>
     * 非标品在确认时录入，标准品可以为空（按整单数量履约）；为空时应付按下单量估算，不能直接把空值当成异常。
     */
    private BigDecimal actualQuantity;

    /**
     * 下单数量；实重为空时的兜底计价数量。
     */
    private BigDecimal orderedQuantity;

    /**
     * 订单行保存的售价。
     */
    private BigDecimal lockedUnitPrice;

    /**
     * 下单金额（下单量 × 锁定价），优惠按比例分摊的分母。
     */
    private BigDecimal orderedLineAmount;
}
