package com.xsy.scm.payment.domain.dto;

/**
 * 支付意图的订单、客户和业务员身份必须从真实订单读取并校验，不能信任客户端传入的客户信息。
 */
public record PaymentOrderFact(Long orderId, String orderNo, Long customerId, String customerNameSnapshot,
    Long sellerId, Long settlementCustomerId, String settlementCustomerNameSnapshot, String status,
    String settlementMethod) {
}
