package com.xsy.scm.admin.module.business.purchase.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 询价方案对比 - 单个商品 返回对象
 *
 * @author xsy-scm
 */
@Data
public class InquiryCompareItemVO {

    @Schema(description = "询价明细ID")
    private Long itemId;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "规格ID")
    private Long skuId;

    @Schema(description = "询价数量")
    private BigDecimal requireQuantity;

    @Schema(description = "平均价")
    private BigDecimal avgPrice;

    @Schema(description = "中位价")
    private BigDecimal medianPrice;

    @Schema(description = "各供应商报价")
    private List<InquiryCompareQuoteVO> quotes;
}
