package com.xsy.scm.admin.module.business.purchase.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采购收货单 添加表单
 *
 * @author xsy-scm
 */
@Data
public class ReceiveAddForm {

    @Schema(description = "采购单ID")
    @NotNull(message = "采购单ID不能为空")
    private Long purchaseId;

    @Schema(description = "采购明细ID")
    @NotNull(message = "采购明细ID不能为空")
    private Long itemId;

    @Schema(description = "本次收货数量")
    @NotNull(message = "本次收货数量不能为空")
    private BigDecimal receiveQuantity;

    @Schema(description = "本次实收重量（kg）")
    private BigDecimal receiveWeight;

    @Schema(description = "本次单价（不含税）")
    private BigDecimal unitPrice;

    @Schema(description = "收货人")
    private Long receiveBy;

    @Schema(description = "收货时间")
    private LocalDateTime receiveTime;

    @Schema(description = "状态：1 已收，2 已入库，3 已作废")
    private Integer status;

    @Schema(description = "是否直接入库：true 收货即入库（Q5），false 仅收货待入库确认")
    private Boolean directStock;
}
