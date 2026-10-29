package com.xsy.scm.finance.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import lombok.Data;

@Data
public class FinanceCustomerExpenseVO {
    private Long expenseId;
    private String expenseNo;
    private Long customerId;
    private String customerName;
    private LocalDate expenseMonth;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal taxAmount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal serviceAmount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal freightAmount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal commissionAmount;
    private String reason;
    private OffsetDateTime createdAt;
    private String createdBy;
    private Boolean voided;
    private String voidReason;
    private OffsetDateTime voidedAt;
    private String voidedBy;
    @JsonIgnore
    private Long sellerId;
}
