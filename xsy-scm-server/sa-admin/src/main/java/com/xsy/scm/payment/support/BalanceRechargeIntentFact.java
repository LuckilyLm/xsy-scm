package com.xsy.scm.payment.support;

import java.math.BigDecimal;

/**
 * 余额域创建充值支付意图的入参（支付域的内部契约）。
 *
 * <p>
 * 刻意<b>不复用</b> {@code PaymentIntentCreateForm}：那个表单是「订单支付」的公开入口，客户端能直接调。充值意图必须由余额域创建（先有充值事实、再有钱要付），否则客户端可以拿任意
 * rechargeId 拼一笔支付。
 *
 * <p>
 * {@code customerName} 是余额域解析后传入的客户名快照，支付域不再回查客户主档； {@code mockScenario} 只有本地模拟渠道可带，真实渠道带它一律拒收。
 */
public record BalanceRechargeIntentFact(Long rechargeId, String rechargeNo, Long customerId, String customerName,
        BigDecimal amount, String provider, String mockScenario, String remark) {
}
