package com.xsy.scm.payment.domain.dto;

import com.xsy.scm.order.constant.ScmOrderRefundStatusEnum;
import java.math.BigDecimal;

/**
 * 支付域读取的**业务退款单事实**（只读，来源 {@code order_refund}）。
 *
 * @param refundId
 *            退款单 id
 * @param refundNo
 *            退款单号
 * @param orderId
 *            所属订单
 * @param customerId
 *            退款对象客户；必须与原支付客户一致
 * @param refundAmount
 *            应退金额。支付域提交的退款额必须与它**逐值一致** ——
 *            差一分钱，人工与线上两条退款路径就会各退一部分，账上永远对不齐
 * @param status
 *            {@code PENDING} / {@code COMPLETED}；读侧不过滤，让服务层给出可解释的拒绝原因
 */
public record PaymentOrderRefundFact(Long refundId, String refundNo, Long orderId, Long customerId,
        BigDecimal refundAmount, String status) {

    public boolean completed() {
        // 用订单域的枚举判等，而不是写 "COMPLETED" 字面量：状态值只有一个来源
        return ScmOrderRefundStatusEnum.COMPLETED.name().equals(status);
    }
}
