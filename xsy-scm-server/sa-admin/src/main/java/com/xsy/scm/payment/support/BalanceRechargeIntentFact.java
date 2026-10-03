package com.xsy.scm.payment.support;

import java.math.BigDecimal;

/**
 * 余额域创建充值支付意图的入参（支付域的内部契约）。
 *
 * <p>
 * 刻意**不复用** {@code PaymentIntentCreateForm}：那个表单是「订单支付」的公开入口，
 * 客户端能直接调。充值意图必须由余额域创建（先有充值事实、再有钱要付），
 * 否则客户端可以拿任意 rechargeId 拼一笔支付。
 *
 * @param rechargeId
 *            {@code customer_balance_recharge.id}，作为意图的 {@code source_id}
 * @param rechargeNo
 *            充值单号，冻结进 {@code source_no_snapshot}
 * @param customerId
 *            实际发起充值的业务客户
 * @param customerName
 *            客户名快照（由余额域解析后传入，支付域不再回查客户主档）
 * @param amount
 *            充值金额
 * @param provider
 *            渠道（当前 MOCK）
 * @param mockScenario
 *            仅本地模拟渠道可带；真实渠道带它一律拒收
 */
public record BalanceRechargeIntentFact(Long rechargeId, String rechargeNo, Long customerId, String customerName,
        BigDecimal amount, String provider, String mockScenario, String remark) {
}
