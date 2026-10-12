package com.xsy.scm.payment.domain.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class PaymentRefundRecoveryLogVO {
    private Long id;
    private Long refundId;
    private String operator;
    private String reason;
    private String result;
    private BigDecimal providerAmount;
    private String detail;
    private OffsetDateTime createdAt;
}
