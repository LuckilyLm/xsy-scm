package com.xsy.scm.finance.domain.form;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

import java.time.LocalDate;

/**
 * 财务列表筛选用闭区间的业务日期，统一换算成半开区间的时间戳。
 */
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
