package com.xsy.scm.admin.module.business.supplier.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 供应商厂商信息 返回对象
 *
 * @author xsy-scm
 */
@Data
public class SupplierManufacturerVO {

    @Schema(description = "主键ID")
    private Long manufacturerId;

    @Schema(description = "供应商ID")
    private Long supplierId;

    @Schema(description = "厂商名称")
    private String manufacturerName;

    @Schema(description = "资质证件")
    private String qualificationFile;

    @Schema(description = "资质到期日期")
    private LocalDate qualificationExpireDate;

    @Schema(description = "质检报告文件")
    private String inspectReportFile;

    @Schema(description = "状态：1 启用，2 停用")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
