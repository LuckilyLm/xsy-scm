package com.xsy.scm.finance.domain.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 退款单已完成只代表业务流程结束，不代表钱已实际退付。财务付款需单独登记，其凭据号不能直接复制订单凭据。
 */
@Data
public class FinanceRefundFactDto {

    private Long refundId;
    private Long orderId;

    /**
     * {@code xsy_order_refund.customer_id}，退款付款的收款对方，也是 CUSTOMER 侧范围判定的入口。
     */
    private Long customerId;

    /**
     * 仅在原退款单已完成、可以付款时为 true。
     */
    private boolean completed;

    /**
     * 本次退款的应退金额。付款金额必须与之逐值相等（4 位小数，不先四舍五入到 2 位再比）。
     */
    private BigDecimal refundAmount;
}
