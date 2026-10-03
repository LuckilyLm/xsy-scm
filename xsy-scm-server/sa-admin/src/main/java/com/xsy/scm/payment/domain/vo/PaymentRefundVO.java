package com.xsy.scm.payment.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

/** 渠道退款事实。 */
@Data
public class PaymentRefundVO {

    private Long id;

    private String refundNo;

    private Long intentId;

    private Long transactionId;

    private String provider;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal amount;

    /** 业务来源，当前只有 {@code ORDER_REFUND}。 */
    private String sourceType;

    private Long sourceId;

    private String status;

    private String providerRefundNo;

    private String mockScenario;

    private String reason;

    private OffsetDateTime refundedAt;

    private String failureCode;

    private String failureMessage;

    private OffsetDateTime createdAt;

    private String createdBy;
}
