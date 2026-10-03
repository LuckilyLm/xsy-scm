package com.xsy.scm.payment.support;

import java.math.BigDecimal;

/** 支付域定义的内部消费契约；实现必须与支付共用本地事务。 */
public interface BalanceConsumptionSink {

    BalanceConsumptionResult consumeForPayment(Long intentId, Long customerId, Long settlementCustomerId,
            BigDecimal amount);
}
