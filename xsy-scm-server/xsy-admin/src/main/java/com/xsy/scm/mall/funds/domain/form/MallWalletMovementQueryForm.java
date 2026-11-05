package com.xsy.scm.mall.funds.domain.form;

import com.xsy.scm.balance.constant.ScmBalanceDirectionEnum;
import com.xsy.scm.balance.constant.ScmBalanceMovementTypeEnum;
import com.xsy.scm.common.validation.ScmEnumValue;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MallWalletMovementQueryForm extends MallFundsPageForm {
    @ScmEnumValue(enumClass = ScmBalanceMovementTypeEnum.class, message = "流水类型无效")
    private String type;

    @ScmEnumValue(enumClass = ScmBalanceDirectionEnum.class, message = "收支方向无效")
    private String direction;
}
