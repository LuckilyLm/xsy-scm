package net.lab1024.sa.admin.module.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 付款的业务来源类型；本期只有退款一个值，无来源的预付为 {@code null}。
 *
 * <p>{@code (source_type, source_id)} 上的 {@code uk_finance_payment_source_active} 是 Q19 / Q26
 * 要求的「退款付款库级唯一」：同一张 {@code order_refund} 最多一笔正式退款付款。
 *
 * <p><b>反向付款行的来源必须为 {@code NULL}</b>（D-3，{@code ck_finance_payment_reverse_no_source}）：
 * 该唯一索引的谓词是 {@code source_id IS NOT NULL}，反向行若沿用原行来源就会与原行抢同一个键，
 * 结果是「登错一笔退款付款后再也反向不掉」。
 *
 * <p>本期不新增分期退款、部分退款付款、多渠道拆分退款（Q19）。
 */
@Getter
@RequiredArgsConstructor
public enum ScmFinancePaymentSourceTypeEnum {

    /**
     * 退款付款：{@code source_id} = {@code order_refund.id}，金额必须等于
     * {@code order_refund.refund_amount}，且该退款必须已 {@code COMPLETED}。
     *
     * <p>{@code order_refund.COMPLETED} 只是订单域的业务事实，<b>不等于真实资金已付出</b>（Q19）；
     * 付款也<b>不冲减应收</b>（Q27）—— Return 负责红冲，Refund Payment 只负责真实资金退付。
     */
    ORDER_REFUND("订单退款");

    private final String desc;
}
