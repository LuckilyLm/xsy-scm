package com.xsy.scm.payment.domain.dto;

/**
 * 支付域读取的销售订单事实（只读，来源 {@code sales_order}）。
 *
 * <p>
 * 支付意图的来源身份不能由客户端自己拼：创建意图时按订单 id 解析出正式事实， 校验订单存在、属于该客户、在当前调用者的订单数据范围内，并冻结真实的客户名与订单号。 否则 {@code customer_name_snapshot}
 * 里会躺着一个客户 id 字符串，支付成功后又顺着它进 Finance。
 */
public record PaymentOrderFact(Long orderId, String orderNo, Long customerId, String customerNameSnapshot,
        Long sellerId, Long settlementCustomerId, String settlementCustomerNameSnapshot, String status) {
}
