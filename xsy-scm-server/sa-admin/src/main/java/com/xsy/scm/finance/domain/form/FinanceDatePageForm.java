package com.xsy.scm.finance.domain.form;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

import java.time.LocalDate;

/** Finance list filters use closed business-date ranges, converted centrally to half-open timestamps. */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class FinanceDatePageForm extends PageParam {

    @NotNull(message = "起始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    protected FinanceDatePageForm() {
        setPageNum(1L);
        setPageSize(20L);
    }
}
