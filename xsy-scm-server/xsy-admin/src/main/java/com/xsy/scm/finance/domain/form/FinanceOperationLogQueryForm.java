package com.xsy.scm.finance.domain.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 按所属财务单据查询操作日志。
 */
@Data
public class FinanceOperationLogQueryForm {

    @NotNull(message = "财务单据类型不能为空")
    private String businessType;

    @NotNull(message = "财务单据编号不能为空")
    @Positive(message = "财务单据编号必须大于0")
    private Long businessId;
}
