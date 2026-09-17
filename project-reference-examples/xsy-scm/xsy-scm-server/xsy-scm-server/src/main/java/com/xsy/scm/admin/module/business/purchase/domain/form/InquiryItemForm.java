package com.xsy.scm.admin.module.business.purchase.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 询价明细 表单
 *
 * @author xsy-scm
 */
@Data
public class InquiryItemForm {

    @Schema(description = "商品ID")
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @Schema(description = "规格ID")
    private Long skuId;

    @Schema(description = "询价数量")
    @NotNull(message = "询价数量不能为空")
    private BigDecimal requireQuantity;
}
