package com.xsy.scm.admin.module.business.finance.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 发票 添加表单
 *
 * @author xsy-scm
 */
@Data
public class InvoiceAddForm {

    @Schema(description = "发票号")
    @NotBlank(message = "发票号不能为空")
    private String invoiceNo;

    @Schema(description = "关联订单ID")
    @NotNull(message = "关联订单ID不能为空")
    private Long orderId;

    @Schema(description = "价税合计")
    @NotNull(message = "价税合计不能为空")
    private BigDecimal amount;

    @Schema(description = "税率")
    private BigDecimal taxRate;

    @Schema(description = "关联发票号（多张时逗号分隔）")
    private String relateInvoiceNos;
}
