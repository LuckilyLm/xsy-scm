package com.xsy.scm.admin.module.business.purchase.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 供应商报价 返回对象
 *
 * @author xsy-scm
 */
@Data
public class InquiryQuoteVO {

    @Schema(description = "主键ID")
    private Long quoteId;

    @Schema(description = "询价单ID")
    private Long inquiryId;

    @Schema(description = "询价明细ID")
    private Long itemId;

    @Schema(description = "供应商ID")
    private Long supplierId;

    @Schema(description = "报价（不含税）")
    private BigDecimal quotePrice;

    @Schema(description = "报价时间")
    private LocalDateTime quoteTime;

    @Schema(description = "供应商综合评分（0~100）")
    private BigDecimal score;
}
