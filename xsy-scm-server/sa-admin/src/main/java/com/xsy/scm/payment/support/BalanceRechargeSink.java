package com.xsy.scm.payment.support;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 支付成功后的**充值落账入口**（由余额域实现，支付域调用）。
 *
 * <p>
 * 接口定义在**调用方**（支付域）而不是实现方（余额域）：这样支付域只依赖自己的接口，
 * 余额域依赖支付域来创建意图 —— 编译期的依赖是单向的，不会出现两个域互相 import。
 *
 * <p>
 * <b>为什么需要它</b>：充值的两条事实要回答两个不同问题 ——
 * Finance 收款事实说「公司实际进账了多少」，余额流水说「这笔钱形成了多少钱包权益」。
 * 前者由支付域直接写财务，后者必须由余额域解释（钱包规则属于余额域）。
 *
 * <p>
 * <b>必须在支付成功所在事务内调用</b>：两者要么一起成、要么一起不成，
 * 否则会出现「钱进来了但钱包没加」这种对客户最不友好的半截状态。
 */
public interface BalanceRechargeSink {

    /**
     * 充值支付成功 → 钱包权益增加。
     *
     * <p>
     * 实现方必须按 {@code (PAYMENT_TRANSACTION, transactionId)} 做来源幂等：
     * 同一个支付回调被重复投递多少次，也只能产生一条充值流水。
     *
     * @param rechargeId
     *            {@code customer_balance_recharge.id}
     * @param customerId
     *            实际发起充值的业务客户；钱包取它的结算主体
     * @param providerAmount
     *            渠道实收金额（不是本地应付金额）
     * @param transactionId
     *            {@code payment_transaction.id}，作为余额流水的唯一来源键
     * @param succeededAt
     *            渠道成功时间
     */
    void rechargeFromPayment(Long rechargeId, Long customerId, BigDecimal providerAmount, Long transactionId,
            OffsetDateTime succeededAt);
}
