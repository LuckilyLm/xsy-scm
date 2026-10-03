package com.xsy.scm.payment.domain.dto;

/**
 * 支付域读取的**销售订单事实**（只读，来源 {@code sales_order}）。
 *
 * <p>
 * 存在的意义：支付意图的来源身份**不能由客户端自己拼**。创建意图时按订单 id 解析出正式事实，
 * 校验「订单存在 / 属于这个客户 / 在当前调用者的订单数据范围内」，并冻结**真实的**客户名与订单号 ——
 * 否则 {@code customer_name_snapshot} 里会躺着一个客户 id 字符串，支付成功后又顺着它进 Finance。
 *
 * @param orderId
 *            订单 id
 * @param orderNo
 *            正式订单号，冻结进 {@code payment_intent.source_no_snapshot}
 * @param customerId
 *            订单客户；必须等于请求里的 customerId
 * @param customerNameSnapshot
 *            订单上的客户名快照，冻结进 {@code payment_intent.customer_name_snapshot}
 * @param sellerId
 *            订单业务员，用于订单数据范围判定
 */
public record PaymentOrderFact(Long orderId, String orderNo, Long customerId, String customerNameSnapshot,
        Long sellerId) {
}
