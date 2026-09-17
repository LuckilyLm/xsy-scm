package com.xsy.scm.admin.module.business.purchase.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 询价方案对比 - 单条报价 返回对象
 *
 * <p>平均价 / 中位价差额 = |报价 − 平均价 / 中位价|。</p>
 *
 * @author xsy-scm
 */
@Data
public class InquiryCompareQuoteVO {

    @Schema(description = "报价ID")
    private Long quoteId;

    @Schema(description = "供应商ID")
    private Long supplierId;

    @Schema(description = "报价（不含税）")
    private BigDecimal quotePrice;

    @Schema(description = "供应商综合评分（0~100）")
    private BigDecimal score;

    @Schema(description = "与平均价的差额")
    private BigDecimal avgDiff;

    @Schema(description = "与中位价的差额")
    private BigDecimal medianDiff;
}
