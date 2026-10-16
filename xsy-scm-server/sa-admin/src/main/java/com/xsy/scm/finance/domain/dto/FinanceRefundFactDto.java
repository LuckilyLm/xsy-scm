package com.xsy.scm.finance.domain.dto;

import java.math.BigDecimal;
import lombok.Data;

/** 退款单已完成只代表业务流程结束，不代表钱已实际退付。财务付款需单独登记，其凭据号不能直接复制订单凭据。 */
@Data
public class FinanceRefundFactDto {

    private Long refundId;
    private Long orderId;

    /**
     * {@code xsy_order_refund.customer_id}，退款付款的收款对方，也是 CUSTOMER 侧范围判定的入口。
     */
    private Long customerId;

    /** True only when the source order refund has completed and may be paid. */
    private boolean completed;

    /**
     * 本次退款的应退金额。付款金额必须与它<b>逐值相等</b>（scale 4，不四舍五入到 2 位再比）。
     */
    private BigDecimal refundAmount;
}
