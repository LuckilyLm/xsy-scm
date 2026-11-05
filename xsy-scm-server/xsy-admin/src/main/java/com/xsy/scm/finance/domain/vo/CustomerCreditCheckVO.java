package com.xsy.scm.finance.domain.vo;

import lombok.Data;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CustomerCreditCheckVO {
    private Long settlementCustomerId;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal creditLimit;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal openReceivableAmount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal confirmedOrderAmount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal requestedOrderAmount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal projectedExposure;
    private Boolean overLimit;
    private Boolean overdue;
    private LocalDate earliestOverdueDate;
    private String amountThresholdHint;
    private Boolean allowed;
}
