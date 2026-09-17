package com.xsy.scm.admin.module.business.purchase.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 无单收货 添加表单
 *
 * @author xsy-scm
 */
@Data
public class ReceiveNoOrderAddForm {

    @Schema(description = "供应商ID")
    @NotNull(message = "供应商ID不能为空")
    private Long supplierId;

    @Schema(description = "商品ID")
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @Schema(description = "规格ID")
    @NotNull(message = "规格ID不能为空")
    private Long skuId;

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

    @Schema(description = "商品备注")
    private String remark;

    @Schema(description = "是否直接入库：true 收货即入库，false 仅收货待入库确认")
    private Boolean directStock;
}
