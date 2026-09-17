package com.xsy.scm.admin.module.business.supplier.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 供应商厂商信息 添加表单
 *
 * @author xsy-scm
 */
@Data
public class SupplierManufacturerAddForm {

    @Schema(description = "供应商ID")
    @NotNull(message = "供应商ID不能为空")
    private Long supplierId;

    @Schema(description = "厂商名称")
    @NotBlank(message = "厂商名称不能为空")
    private String manufacturerName;

    @Schema(description = "资质证件")
    private String qualificationFile;

    @Schema(description = "资质到期日期")
    private LocalDate qualificationExpireDate;

    @Schema(description = "质检报告文件")
    private String inspectReportFile;

    @Schema(description = "状态：1 启用，2 停用")
    private Integer status;
}
