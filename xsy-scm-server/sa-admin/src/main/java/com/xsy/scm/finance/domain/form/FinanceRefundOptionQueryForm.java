package com.xsy.scm.finance.domain.form;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 已完成且未付款退款单的分页选择器（按范围过滤）。
 */
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
