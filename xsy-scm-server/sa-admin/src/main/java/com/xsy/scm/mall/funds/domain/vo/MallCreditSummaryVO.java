package com.xsy.scm.mall.funds.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
public class MallCreditSummaryVO {
    private Boolean sharedSettlement;
    private Boolean creditLimitVisible;
    private String creditPeriodType;
    private Integer creditPeriodValue;
    private String creditPeriodUnit;
    private Integer settleDay;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal creditAmountThreshold;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal creditLimit;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal openReceivableAmount;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal confirmedOrderAmount;

    private LocalDate earliestOverdueDate;
}
