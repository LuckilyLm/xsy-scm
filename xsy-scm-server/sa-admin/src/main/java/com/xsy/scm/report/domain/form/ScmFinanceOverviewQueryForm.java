package com.xsy.scm.report.domain.form;

import com.xsy.scm.report.support.ScmReportDateFilter;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 财务流水指标与期末余额的日期范围。
 */
@Data
public class ScmFinanceOverviewQueryForm implements ScmReportDateFilter {

    @NotNull(message = "起始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;
}
