package com.xsy.scm.finance.support;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 「一笔支付交易已成功收款」的**事实**：支付域交给财务域的最小字段集。
 *
 * <p>
 * 为什么不让支付域直接拼一个 {@code FinanceReceiptAddForm}：那个表单是**人工登记**的入口，
 * 它的字段（方式、时点、外部凭据）都假设「有人在填」。系统来源的收款必须由财务域自己
 * 决定方式（在线支付）、自己解析结算客户、自己写来源键 —— 让支付域去凑一个人工表单，
 * 等于把财务域的规则复制到支付域，两边迟早不一致。
 *
 * @param transactionId
 *            {@code payment_transaction.id}；财务域把它记成唯一来源键
 * @param customerId
 *            付款客户；结算客户快照由财务域按客户域规则解析，不由支付域传
 * @param providerAmount
 *            **渠道实际成功捕获/结算的金额**。财务收款金额认它，不认本地应付金额 ——
 *            本地应付 100、渠道实收 98 时，凭空登记 100 会让账实不符
 * @param succeededAt
 *            渠道成功时间（不是本地登记时间）
 * @param providerTransactionNo
 *            渠道交易号，落进 {@code external_reference} 供对账与人工核对
 */
public record FinancePaymentReceiptFact(Long transactionId, Long customerId, BigDecimal providerAmount,
        OffsetDateTime succeededAt, String providerTransactionNo) {
}
