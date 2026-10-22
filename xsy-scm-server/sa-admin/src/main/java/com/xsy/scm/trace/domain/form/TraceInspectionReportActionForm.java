package com.xsy.scm.trace.domain.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class TraceInspectionReportActionForm {
    @NotNull(message = "请刷新报告后重试")
    @PositiveOrZero(message = "报告版本不能小于零")
    private Integer version;
}
