package com.xsy.scm.payment.support;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 充值落账入口：由余额域实现，支付域在支付成功所在事务内调用。
 *
 * <p>
 * 接口定义在调用方（支付域），使编译期依赖保持单向 —— 余额域依赖支付域创建意图，反向不成立。与 Finance 收款事实的分工：收款事实说「公司实际进账多少」，余额流水说「这笔钱形成多少钱包权益」，后者由持有钱包规则的余额域解释。
 * 同一事务内落账，避免「钱进来了但钱包没加」的半截状态。
 */
public interface BalanceRechargeSink {

    /**
     * 充值支付成功 → 钱包权益增加。
     *
     * <p>
     * 实现方必须按 {@code (PAYMENT_TRANSACTION, transactionId)} 幂等：同一支付回调重复投递也只产生一条充值流水。
     */
    void rechargeFromPayment(Long rechargeId, Long customerId, BigDecimal providerAmount, Long transactionId,
            OffsetDateTime succeededAt);
}
