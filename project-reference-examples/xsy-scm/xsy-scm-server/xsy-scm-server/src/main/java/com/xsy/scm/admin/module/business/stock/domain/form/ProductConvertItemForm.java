package com.xsy.scm.admin.module.business.stock.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品转换明细 表单
 *
 * @author xsy-scm
 */
@Data
public class ProductConvertItemForm {

    @Schema(description = "原商品ID")
    @NotNull(message = "原商品ID不能为空")
    private Long sourceProductId;

    @Schema(description = "原规格ID")
    @NotNull(message = "原规格ID不能为空")
    private Long sourceSkuId;

    @Schema(description = "出库数量")
    @NotNull(message = "出库数量不能为空")
    private BigDecimal sourceQuantity;

    @Schema(description = "出库重量kg")
    private BigDecimal sourceWeight;

    @Schema(description = "目标商品ID")
    @NotNull(message = "目标商品ID不能为空")
    private Long targetProductId;

    @Schema(description = "目标规格ID")
    @NotNull(message = "目标规格ID不能为空")
    private Long targetSkuId;

    @Schema(description = "入库数量")
    @NotNull(message = "入库数量不能为空")
    private BigDecimal targetQuantity;

    @Schema(description = "入库重量kg")
    private BigDecimal targetWeight;

    @Schema(description = "入库单价（不含税）")
    @NotNull(message = "入库单价不能为空")
    private BigDecimal targetUnitPrice;
}
