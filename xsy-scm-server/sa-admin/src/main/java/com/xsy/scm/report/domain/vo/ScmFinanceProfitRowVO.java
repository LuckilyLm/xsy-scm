package com.xsy.scm.report.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import lombok.Data;

/**
 * 销售毛利行；任一来源成本缺失时毛利为 null。
 */
@Data
public class ScmFinanceProfitRowVO {
    private Long dimensionId;
    private String dimensionName;
    private String dimensionCode;
    private LocalDate bizDate;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal revenueAmount;

    /**
     * 销售成本净额；已验收的退货入库成本作为负数冲减。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal salesCostAmount;

    /**
     * 促销赠品成本（满赠赠品的出库成本，正数表示成本）。
     *
     * <p>
     * 与 {@link #salesCostAmount} 分开：赠品不减收入，它是订单的履约成本之一；单独一列才能直接回答 「这个月营销活动送掉多少成本」，而不必回头从库存流水重拼。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal giftCostAmount;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal grossProfit;

    /**
     * 百分数，取值 0–100，不是 0–1 的比例。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal grossMarginRate;

    private Long costMissingCount;
}
