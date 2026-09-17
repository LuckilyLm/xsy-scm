package com.xsy.scm.admin.module.business.purchase.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 询价明细 返回对象
 *
 * @author xsy-scm
 */
@Data
public class InquiryItemVO {

    @Schema(description = "主键ID")
    private Long itemId;

    @Schema(description = "询价单ID")
    private Long inquiryId;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "规格ID")
    private Long skuId;

    @Schema(description = "询价数量")
    private BigDecimal requireQuantity;
}
