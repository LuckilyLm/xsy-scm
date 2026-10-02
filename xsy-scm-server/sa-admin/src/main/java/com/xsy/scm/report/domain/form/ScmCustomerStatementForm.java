package com.xsy.scm.report.domain.form;

import java.time.LocalDate;

import com.xsy.scm.report.support.ScmReportDateFilter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** A frozen statement belongs to a historical settlement account, not today's group hierarchy. */
@Data
public class ScmCustomerStatementForm implements ScmReportDateFilter {
    @NotNull(message = "结算方不能为空")
    @Positive(message = "结算方编号必须大于0")
    private Long settlementCustomerId;
    @Positive(message = "客户编号必须大于0")
    private Long customerId;
    @NotNull(message = "起始日期不能为空")
    private LocalDate startDate;
    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;
}
