package com.xsy.scm.report.domain.form;

import java.util.List;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ScmCustomerMonthlyProfitQueryForm extends ScmFinanceProfitQueryForm {
    @Size(max = 30, message = "导出字段不能超过30列")
    private List<String> exportColumns;
}
