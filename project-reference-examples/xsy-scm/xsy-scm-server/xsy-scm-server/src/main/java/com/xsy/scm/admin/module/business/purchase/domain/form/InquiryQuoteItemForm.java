package com.xsy.scm.admin.module.business.purchase.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 供应商报价明细 表单
 *
 * @author xsy-scm
 */
@Data
public class InquiryQuoteItemForm {

    @Schema(description = "询价明细ID")
    @NotNull(message = "询价明细ID不能为空")
    private Long itemId;

    @Schema(description = "报价（不含税）")
    @NotNull(message = "报价不能为空")
    private BigDecimal quotePrice;

    @Schema(description = "供应商综合评分（0~100）")
    private BigDecimal score;
}
