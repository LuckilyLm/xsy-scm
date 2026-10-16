package com.xsy.scm.payment.domain.dto;

import com.xsy.scm.order.constant.ScmOrderRefundStatusEnum;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 渠道退款前确认退款单客户与原支付客户相同，金额与批准退款额一致。退款状态的拒绝原因由服务层判断。 */
public record PaymentOrderRefundFact(Long refundId, String refundNo, Long orderId, Long customerId,
        BigDecimal refundAmount, String status, OffsetDateTime completedAt) {

    public boolean completed() {
        // 用订单域的枚举判等，而不是写 "COMPLETED" 字面量：状态值只有一个来源
        return ScmOrderRefundStatusEnum.COMPLETED.name().equals(status);
    }
}
