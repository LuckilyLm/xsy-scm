package net.lab1024.sa.admin.module.scm.finance.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 退款付款所需的**订单退款事实**，由 {@code FinancePaymentSourceDao} 只读取得（Q19）。
 *
 * <p>{@code order_refund.COMPLETED} 只代表订单域的退款业务已完成，<b>不代表资金已付出</b>；
 * 真实退付由 {@code finance_payment} 表达，两者是两个独立事实，因此本 DTO 只用于校验，
 * 付款决不由 {@code OrderRefundService.complete} 自动触发。
 *
 * <p>刻意<b>不含</b> {@code order_refund.external_reference}：那是订单侧的凭据文本，
 * 财务付款的凭据号必须独立登记（设计稿 §6 / 第二批 Q25），复制过来会让同一凭证号
 * 在两个域里被当成同一笔钱。
 */
@Data
public class FinanceRefundFactDto {

    private Long refundId;

    /**
     * {@code order_refund.customer_id}，退款付款的收款对方，也是 CUSTOMER 侧范围判定的入口。
     */
    private Long customerId;

    /**
     * {@code PENDING / COMPLETED}；只有 {@code COMPLETED} 可付款。
     */
    private String status;

    /**
     * 本次退款的应退金额。付款金额必须与它**逐值相等**（scale 4，不四舍五入到 2 位再比）。
     */
    private BigDecimal refundAmount;
}
