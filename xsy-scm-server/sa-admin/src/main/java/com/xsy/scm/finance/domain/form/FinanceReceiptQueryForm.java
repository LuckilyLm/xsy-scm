package com.xsy.scm.finance.domain.form;

import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 收款列表、详情和导出共用筛选条件。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FinanceReceiptQueryForm extends FinanceDatePageForm {

    @Positive(message = "收款单编号必须大于0")
    private Long receiptId;

    @Positive(message = "客户编号必须大于0")
    private Long customerId;

    private String method;

    private String entryType;

    private Boolean pendingOnly;
}
