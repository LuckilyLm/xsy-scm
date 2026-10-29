package com.xsy.scm.report.domain.vo;

import java.math.BigDecimal;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ScmCustomerMonthlyProfitVO extends ScmFinanceProfitRowVO {
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal taxAmount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal serviceAmount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal freightAmount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal commissionAmount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal expenseAmount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal netProfit;
}
