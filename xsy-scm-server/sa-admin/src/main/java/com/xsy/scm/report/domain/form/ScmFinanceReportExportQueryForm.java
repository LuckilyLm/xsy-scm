package com.xsy.scm.report.domain.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 导出筛选不包含分页；导出受行数限制保护，并完整输出。
 */
@Data
public class ScmFinanceReportExportQueryForm implements ScmFinanceReportFilter {

    @NotNull(message = "起始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    @Size(max = 120, message = "关键字长度不能超过120")
    private String keyword;
}
