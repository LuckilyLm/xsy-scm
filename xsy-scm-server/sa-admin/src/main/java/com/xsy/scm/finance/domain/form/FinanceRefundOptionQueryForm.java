package com.xsy.scm.finance.domain.form;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

/** Paginated, scope-filtered picker for completed refunds not yet paid by Finance. */
@Data
@EqualsAndHashCode(callSuper = true)
public class FinanceRefundOptionQueryForm extends PageParam {

    @Size(max = 120, message = "退款搜索词长度不能超过120")
    private String keyword;

    public FinanceRefundOptionQueryForm() {
        setPageNum(1L);
        setPageSize(20L);
    }
}
