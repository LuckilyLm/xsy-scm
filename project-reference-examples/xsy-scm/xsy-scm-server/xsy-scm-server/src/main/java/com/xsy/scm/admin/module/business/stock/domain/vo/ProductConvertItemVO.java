package com.xsy.scm.admin.module.business.stock.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品转换明细 返回对象
 *
 * @author xsy-scm
 */
@Data
public class ProductConvertItemVO {

    @Schema(description = "主键ID")
    private Long itemId;

    @Schema(description = "转换单ID")
    private Long convertId;

    @Schema(description = "原商品ID")
    private Long sourceProductId;

    @Schema(description = "原规格ID")
    private Long sourceSkuId;

    @Schema(description = "出库数量")
    private BigDecimal sourceQuantity;

    @Schema(description = "出库重量kg")
    private BigDecimal sourceWeight;

    @Schema(description = "目标商品ID")
    private Long targetProductId;

    @Schema(description = "目标规格ID")
    private Long targetSkuId;

    @Schema(description = "入库数量")
    private BigDecimal targetQuantity;

    @Schema(description = "入库重量kg")
    private BigDecimal targetWeight;

    @Schema(description = "入库单价（不含税）")
    private BigDecimal targetUnitPrice;

    @Schema(description = "入库金额（不含税）")
    private BigDecimal targetAmount;
}
