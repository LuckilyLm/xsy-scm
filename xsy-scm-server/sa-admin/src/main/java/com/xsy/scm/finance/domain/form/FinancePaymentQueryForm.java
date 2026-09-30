package com.xsy.scm.finance.domain.form;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 付款列表、详情和导出共用筛选条件。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FinancePaymentQueryForm extends FinanceDatePageForm {

    @Positive(message = "付款单编号必须大于0")
    private Long paymentId;

    private String counterpartyType;

    @Positive(message = "往来方编号必须大于0")
    private Long counterpartyId;

    @Size(max = 120, message = "往来方名称长度不能超过120")
    private String counterpartyName;

    private String method;

    private String sourceType;

    private String entryType;

    private Boolean pendingOnly;
}
