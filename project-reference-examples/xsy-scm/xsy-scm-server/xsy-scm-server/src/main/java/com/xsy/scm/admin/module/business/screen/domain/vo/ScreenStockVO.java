package com.xsy.scm.admin.module.business.screen.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存大屏 返回对象
 *
 * @author xsy-scm
 */
@Data
public class ScreenStockVO {

    @Schema(description = "在库商品规格数")
    private Long productCount;

    @Schema(description = "库存总数量")
    private BigDecimal totalQuantity;

    @Schema(description = "结存总成本（不含税）")
    private BigDecimal totalCost;

    @Schema(description = "预警数（低于下限）")
    private Long warnCount;
}
