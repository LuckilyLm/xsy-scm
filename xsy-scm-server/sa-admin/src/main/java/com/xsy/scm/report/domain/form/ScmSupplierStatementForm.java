package com.xsy.scm.report.domain.form;

import java.time.LocalDate;

import com.xsy.scm.report.support.ScmReportDateFilter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ScmSupplierStatementForm implements ScmReportDateFilter {
    @NotNull(message = "供应商不能为空")
    @Positive(message = "供应商编号必须大于0")
    private Long supplierId;
    @Positive(message = "仓库编号必须大于0")
    private Long warehouseId;
    @NotNull(message = "起始日期不能为空")
    private LocalDate startDate;
    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;
}
