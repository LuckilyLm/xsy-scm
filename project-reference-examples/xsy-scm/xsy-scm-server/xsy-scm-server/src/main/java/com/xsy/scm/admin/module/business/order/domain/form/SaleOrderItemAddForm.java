package com.xsy.scm.admin.module.business.order.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售订单明细 添加表单
 *
 * @author xsy-scm
 */
@Data
public class SaleOrderItemAddForm {

    @Schema(description = "订单ID")
    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @Schema(description = "商品ID")
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @Schema(description = "规格ID")
    private Long skuId;

    @Schema(description = "下单数量")
    @NotNull(message = "下单数量不能为空")
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
}
