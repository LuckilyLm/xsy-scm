package com.xsy.scm.admin.module.business.stock.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存流水 返回对象（只读）
 *
 * @author xsy-scm
 */
@Data
public class StockFlowVO {

    @Schema(description = "流水ID")
    private Long flowId;

    @Schema(description = "流水号")
    private String flowNo;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "规格ID")
    private Long skuId;

    @Schema(description = "仓库ID")
    private Long warehouseId;

    @Schema(description = "批次ID")
    private Long batchId;

    @Schema(description = "流水类型：1 采购入库，2 销售出库，3 退货入库，4 报损，5 报溢，6 盘点调整，7 规格转换出，8 规格转换入")
    private Integer flowType;

    @Schema(description = "关联业务：1 采购，2 订单，3 分拣，4 盘点，5 报损报溢，6 规格转换")
    private Integer bizType;

    @Schema(description = "关联业务单ID")
    private Long bizId;

    @Schema(description = "方向：1 入，2 出")
    private Integer direction;

    @Schema(description = "变动数量（正数）")
    private BigDecimal quantity;

    @Schema(description = "变动重量（kg，正数）")
    private BigDecimal weight;

    @Schema(description = "变动单价（不含税）")
    private BigDecimal unitPrice;

    @Schema(description = "变动金额（不含税）")
    private BigDecimal amount;

    @Schema(description = "变动前数量")
    private BigDecimal beforeQuantity;

    @Schema(description = "变动后数量")
    private BigDecimal afterQuantity;

    @Schema(description = "变动前加权平均成本")
    private BigDecimal beforeAvgCost;

    @Schema(description = "变动后加权平均成本")
    private BigDecimal afterAvgCost;

    @Schema(description = "操作人")
    private Long operateBy;

    @Schema(description = "操作时间")
    private LocalDateTime operateTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
