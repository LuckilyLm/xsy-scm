package com.xsy.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 付款的业务来源类型；退款付款使用该值，供应商预付款没有来源并保存为 {@code null}。
 *
 * <p>
 * {@code (source_type, source_id)} 上的 {@code uk_finance_payment_source_active} 保证同一张 {@code order_refund} 最多一笔正式退款付款。
 *
 * <p>
 * <b>反向付款行的来源必须为 {@code NULL}</b>（{@code ck_finance_payment_reverse_no_source}）：该唯一索引的谓词是
 * {@code source_id IS NOT NULL}，反向行若沿用原行来源就会与原行抢同一个键，结果是「登错一笔退款付款后再也反向不掉」。
 *
 * <p>
 * 分期、部分退款付款和多渠道拆分退款不属于此来源类型。
 */
@Getter
@RequiredArgsConstructor
public enum ScmFinancePaymentSourceTypeEnum {

    /**
     * 退款付款：{@code source_id} = {@code order_refund.id}，金额必须等于 {@code order_refund.refund_amount}，且该退款必须已
     * {@code COMPLETED}。
     *
     * <p>
     * {@code order_refund.COMPLETED} 只是订单域的业务事实，<b>不等于真实资金已付出</b>；付款也<b>不冲减应收</b>—— Return 负责红冲，Refund Payment
     * 只负责真实资金退付。
     */
    ORDER_REFUND("订单退款");

    private final String desc;
}
