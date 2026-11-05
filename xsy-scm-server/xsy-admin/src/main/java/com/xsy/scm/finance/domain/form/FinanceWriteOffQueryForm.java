package com.xsy.scm.finance.domain.form;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 核销流水分页筛选；可见范围始终跟随目标应收或应付。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FinanceWriteOffQueryForm extends FinanceDatePageForm {

    @Positive(message = "账款抵扣记录编号必须大于0")
    private Long writeOffId;

    @Positive(message = "用于抵扣的收付款单编号必须大于0")
    private Long sourceId;

    @Positive(message = "结算对象编号必须大于0")
    private Long targetId;

    @Size(max = 64, message = "收付款单号长度不能超过64")
    private String sourceNo;

    @Size(max = 64, message = "待结算单号长度不能超过64")
    private String targetNo;

    private String entryType;
    private String sourceType;

}
