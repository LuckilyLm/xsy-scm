package com.xsy.scm.finance.domain.form;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 应付列表、详情和导出共用筛选条件。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FinancePayableQueryForm extends FinanceDatePageForm {

    @Positive(message = "应付单编号必须大于0")
    private Long payableId;

    @Positive(message = "供应商编号必须大于0")
    private Long supplierId;

    @Size(max = 120, message = "供应商名称长度不能超过120")
    private String supplierName;

    @Size(max = 64, message = "采购单号长度不能超过64")
    private String purchaseOrderNo;

    private String entryType;

    private String settleState;
}
