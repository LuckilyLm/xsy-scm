package com.xsy.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 供应商预付款可以没有业务来源；订单退款付款必须关联退款单。相同来源只能生成一笔有效正式付款，来源身份由唯一索引防重。
 */
@Getter
@RequiredArgsConstructor
public enum ScmFinancePaymentSourceTypeEnum {

    /**
     * 订单退款付款必须引用已完成的退款单，支付金额与批准退款额一致。退款付款是实际资金退付，不再重复处理应收红冲。
     */
    ORDER_REFUND("订单退款");

    private final String desc;
}
