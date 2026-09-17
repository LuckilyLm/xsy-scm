package com.xsy.scm.admin.module.business.order.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售退款单 添加表单
 *
 * @author xsy-scm
 */
@Data
public class SaleRefundAddForm {

    @Schema(description = "订单ID")
    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @Schema(description = "订单明细ID，整单退款时为空")
    private Long itemId;

    @Schema(description = "退款类型：1 仅退款，2 退货退款")
    @NotNull(message = "退款类型不能为空")
    private Integer refundType;

    @Schema(description = "退款金额（不含税）")
    @NotNull(message = "退款金额不能为空")
    private BigDecimal refundAmount;

    @Schema(description = "退款原因")
    private String refundReason;

    @Schema(description = "状态：1 待审核，2 已通过，3 已退款，4 已驳回")
    private Integer status;
}
