package com.xsy.scm.admin.module.business.supplier.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 供应商对账单 添加表单
 *
 * @author xsy-scm
 */
@Data
public class SupplierStatementAddForm {

    @Schema(description = "供应商ID")
    @NotNull(message = "供应商ID不能为空")
    private Long supplierId;

    @Schema(description = "对账周期开始")
    @NotNull(message = "对账周期开始不能为空")
    private LocalDate periodStart;

    @Schema(description = "对账周期结束")
    @NotNull(message = "对账周期结束不能为空")
    private LocalDate periodEnd;

    @Schema(description = "对账总金额（不含税）")
    private BigDecimal totalAmount;

    @Schema(description = "已结算金额（不含税）")
    private BigDecimal paidAmount;
}
