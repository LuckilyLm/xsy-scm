package com.xsy.scm.admin.module.business.purchase.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采购单 添加表单
 *
 * @author xsy-scm
 */
@Data
public class PurchaseOrderAddForm {

    @Schema(description = "供应商ID")
    @NotNull(message = "供应商不能为空")
    private Long supplierId;

    @Schema(description = "采购员ID")
    private Long buyerId;

    @Schema(description = "品类ID，按品类汇总生成采购单时使用")
    private Long categoryId;

    @Schema(description = "采购预估金额（不含税）")
    private BigDecimal totalAmount;

    @Schema(description = "期望到货时间")
    private LocalDateTime expectArriveTime;

    @Schema(description = "采购单二维码地址（文件服务）")
    private String qrcodeUrl;
}
