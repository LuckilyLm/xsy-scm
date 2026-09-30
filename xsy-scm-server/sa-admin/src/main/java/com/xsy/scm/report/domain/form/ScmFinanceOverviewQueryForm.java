package com.xsy.scm.report.domain.form;

import com.xsy.scm.report.support.ScmReportDateFilter;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/** Date window for Finance R1 flow metrics and end-of-period balances. */
@Data
public class ScmFinanceOverviewQueryForm implements ScmReportDateFilter {

    @NotNull(message = "起始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;
}
