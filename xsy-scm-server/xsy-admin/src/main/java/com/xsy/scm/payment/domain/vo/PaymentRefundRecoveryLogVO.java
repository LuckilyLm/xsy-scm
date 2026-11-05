package com.xsy.scm.payment.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Data;

@Data
public class PaymentRefundRecoveryLogVO {
    private Long id;
    private Long refundId;
    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String operator;
    private String reason;
    private String result;
    private BigDecimal providerAmount;
    private String detail;
    private OffsetDateTime createdAt;
}
