package com.xsy.scm.report.domain.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/** Export filters deliberately omit pagination; exports are guarded and streamed in full. */
@Data
public class ScmFinanceReportExportQueryForm implements ScmFinanceReportFilter {

    @NotNull(message = "起始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    @Size(max = 120, message = "关键字长度不能超过120")
    private String keyword;
}
