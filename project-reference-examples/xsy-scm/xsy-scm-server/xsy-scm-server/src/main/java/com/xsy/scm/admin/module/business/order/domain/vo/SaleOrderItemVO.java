package com.xsy.scm.admin.module.business.order.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售订单明细 返回对象
 *
 * @author xsy-scm
 */
@Data
public class SaleOrderItemVO {

    @Schema(description = "明细ID")
    private Long itemId;

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "规格ID")
    private Long skuId;

    @Schema(description = "下单数量")
    private BigDecimal quantity;

    @Schema(description = "成交价快照（不含税）")
    private BigDecimal snapshotPrice;

    @Schema(description = "取价类型：1 基础价，2 客户分级价，3 时价，4 协议价")
    private Integer priceType;

    @Schema(description = "实际重量（kg）")
    private BigDecimal actualWeight;

    @Schema(description = "实际数量")
    private BigDecimal actualQuantity;

    @Schema(description = "明细核算金额（不含税）")
    private BigDecimal itemAmount;

    @Schema(description = "明细状态：1 正常，2 已退款，3 已退货")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
