package com.xsy.scm.finance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Completed, unpaid refund facts available for a customer refund payment. */
@Data
public class FinanceRefundOptionVO {

    private Long refundId;

    private String refundNo;

    private String returnNo;

    private String orderNo;

    private Long customerId;

    private String customerName;

    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal refundAmount;

    private OffsetDateTime completedAt;
}
