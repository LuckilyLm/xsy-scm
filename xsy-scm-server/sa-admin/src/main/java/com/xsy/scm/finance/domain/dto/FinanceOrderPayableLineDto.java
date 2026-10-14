package com.xsy.scm.finance.domain.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 已确认订单的应付行事实：量取实重、价取冻结售价，供应付净额与签收应收共用同一把尺子。
 */
@Data
public class FinanceOrderPayableLineDto {

    /** 所属销售订单主键；批量取数时用来把行归回订单，单订单取数不使用。 */
    private Long salesOrderId;

    /** 订单行主键，与冻结优惠分摊上的 {@code orderItemId} 对齐。 */
    private Long orderItemId;

    /** 实重数量（确认前已录齐， confirmed 订单不存在空值行）。 */
    private BigDecimal actualQuantity;

    /** 订单行冻结售价。 */
    private BigDecimal lockedUnitPrice;

    /** 下单金额（下单量 × 锁定价），优惠等比分摊的分母。 */
    private BigDecimal orderedLineAmount;
}
