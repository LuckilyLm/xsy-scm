package com.xsy.scm.mall.funds.domain.form;

import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.mall.funds.constant.MallReceivableStateEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MallReceivableQueryForm extends MallFundsPageForm {
    @ScmEnumValue(enumClass = MallReceivableStateEnum.class, message = "账单状态无效")
    private String settleState;
}
