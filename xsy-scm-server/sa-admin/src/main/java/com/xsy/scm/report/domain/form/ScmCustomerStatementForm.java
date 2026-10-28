package com.xsy.scm.report.domain.form;

import java.time.LocalDate;

import com.xsy.scm.report.support.ScmReportDateFilter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 已生成的对账单属于当时的历史结算户，不随当前的客户分组层级变化。
 */
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

    /**
     * 是否把过账记录一并列进对账单；不填按「不包含」处理。
     *
     * <p>
     * 过账行只有过账金额，不带应收/收款/核销变动，也不改期末净应收。
     */
    private Boolean includePosting;
}
