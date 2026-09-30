package com.xsy.scm.report.domain.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

import java.time.LocalDate;

/** Aging-free account detail query; ending balances are evaluated at {@code endDate}. */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScmFinanceReportQueryForm extends PageParam implements ScmFinanceReportFilter {

    @NotNull(message = "起始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    @Size(max = 120, message = "关键字长度不能超过120")
    private String keyword;
}
