package com.xsy.scm.payment.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

@Data
public class RefundFundingResultVO {
    private Long refundId;
    private String status;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal refundAmount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal succeededAmount;
    private Long financePaymentId;
    private List<RefundFundingAllocationVO> allocations;
}
