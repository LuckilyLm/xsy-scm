package com.xsy.scm.admin.module.business.order.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售退款单 返回对象
 *
 * @author xsy-scm
 */
@Data
public class SaleRefundVO {

    @Schema(description = "退款单ID")
    private Long refundId;

    @Schema(description = "退款单号")
    private String refundNo;

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "订单明细ID")
    private Long itemId;

    @Schema(description = "退款类型：1 仅退款，2 退货退款")
    private Integer refundType;

    @Schema(description = "退款金额（不含税）")
    private BigDecimal refundAmount;

    @Schema(description = "退款原因")
    private String refundReason;

    @Schema(description = "状态：1 待审核，2 已通过，3 已退款，4 已驳回")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
