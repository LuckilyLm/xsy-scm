package com.xsy.scm.payment.support;

import java.math.BigDecimal;

/**
 * 余额充值意图由余额模块创建，不能复用对客户端开放的订单支付表单。客户名称取充值时保存的事实，模拟场景只用于模拟渠道。
 */
public record BalanceRechargeIntentFact(Long rechargeId, String rechargeNo, Long customerId, String customerName,
    BigDecimal amount, String provider, String mockScenario, String remark) {
}
