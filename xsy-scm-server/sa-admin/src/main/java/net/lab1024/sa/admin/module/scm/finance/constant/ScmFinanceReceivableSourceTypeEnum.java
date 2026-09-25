package net.lab1024.sa.admin.module.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 应收单头的来源类型，与 {@link ScmFinanceEntryTypeEnum} 由
 * {@code ck_finance_receivable_source_pairing} 在库级配对。
 *
 * <p>只有两个值，因为应收只有两种成因（Q1 / Q27）：已签收订单形成正常应收，
 * 已批准退货形成红字应收。手工出库不产生应收（Q5），补单作为独立订单独立生成（Q7），
 * 因此都不需要第三个来源类型。
 */
@Getter
@RequiredArgsConstructor
public enum ScmFinanceReceivableSourceTypeEnum {

    /**
     * 正常应收：{@code source_id} = {@code sales_order.id}。
     */
    SALES_ORDER("销售订单"),

    /**
     * 红字应收：{@code source_id} = {@code order_return.id}。
     */
    ORDER_RETURN("销售退货");

    private final String desc;
}
