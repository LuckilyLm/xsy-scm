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

    /**
     * 促销赠品成本（满赠赠品的出库成本，正数表示成本）。
     *
     * <p>
     * 与 {@link #salesCostAmount} 分开：赠品**不减收入**，它是订单的履约成本之一；
     * 单独一列才能直接回答「这个月营销活动送掉多少成本」，而不必回头从库存流水重拼。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal giftCostAmount;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal grossProfit;

    /** Percentage in 0-100 scale, not a ratio. */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal grossMarginRate;

    private Long costMissingCount;
}
