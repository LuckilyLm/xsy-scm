package com.xsy.scm.finance.domain.form;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 应收列表、详情和导出共用筛选条件。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FinanceReceivableQueryForm extends FinanceDatePageForm {

    @Positive(message = "应收单编号必须大于0")
    private Long receivableId;

    @Positive(message = "客户编号必须大于0")
    private Long customerId;

    @Size(max = 64, message = "订单号长度不能超过64")
    private String orderNo;

    private String entryType;

    private String settleState;
}
