package com.xsy.scm.report.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import lombok.Data;

/** Sales gross-profit row; profit remains null when any source cost is missing. */
@Data
public class ScmFinanceProfitRowVO {
    private Long dimensionId;
    private String dimensionName;
    private String dimensionCode;
    private LocalDate bizDate;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal revenueAmount;

    /** Net sales COGS; accepted return-to-stock cost is a negative adjustment. */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal salesCostAmount;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal grossProfit;

    /** Percentage in 0-100 scale, not a ratio. */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal grossMarginRate;

    private Long costMissingCount;
}
