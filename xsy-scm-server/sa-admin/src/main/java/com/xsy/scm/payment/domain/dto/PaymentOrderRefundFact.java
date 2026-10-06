package com.xsy.scm.payment.domain.dto;

import com.xsy.scm.order.constant.ScmOrderRefundStatusEnum;
import java.math.BigDecimal;

/**
 * 支付域读取的业务退款单事实（只读，来源 {@code order_refund}）。
 *
 * <p>
 * {@code customerId} 必须与原支付客户一致；{@code refundAmount} 必须与支付域提交的退款额逐值一致 —— 差一分钱，人工与线上两条退款路径就会各退一部分。{@code status} 读侧不过滤，
 * 由服务层给出可解释的拒绝原因。
 */
public record PaymentOrderRefundFact(Long refundId, String refundNo, Long orderId, Long customerId,
        BigDecimal refundAmount, String status) {

    public boolean completed() {
        // 用订单域的枚举判等，而不是写 "COMPLETED" 字面量：状态值只有一个来源
        return ScmOrderRefundStatusEnum.COMPLETED.name().equals(status);
    }
}
