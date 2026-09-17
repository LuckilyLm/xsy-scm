package com.xsy.scm.admin.module.business.stock.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存余额 返回对象（只读）
 *
 * @author xsy-scm
 */
@Data
public class StockBalanceVO {

    @Schema(description = "余额ID")
    private Long balanceId;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "规格ID")
    private Long skuId;

    @Schema(description = "仓库ID")
    private Long warehouseId;

    @Schema(description = "批次ID")
    private Long batchId;

    @Schema(description = "当前数量")
    private BigDecimal quantity;

    @Schema(description = "当前重量（kg）")
    private BigDecimal weight;

    @Schema(description = "加权平均成本单价（不含税）")
    private BigDecimal avgCost;

    @Schema(description = "结存总成本（不含税）")
    private BigDecimal totalCost;

    @Schema(description = "预警下限")
    private BigDecimal warnMin;

    @Schema(description = "预警上限")
    private BigDecimal warnMax;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
