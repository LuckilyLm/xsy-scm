package com.xsy.scm.customer.domain.form;

import jakarta.validation.constraints.NotNull;
import com.xsy.scm.common.constant.ScmCustomerStatusEnum;
import com.xsy.scm.common.validation.ScmEnumValue;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 客户状态变更命令。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerStatusForm extends CustomerDeleteForm {

    @NotNull(message = "客户状态不能为空")
    @ScmEnumValue(enumClass = ScmCustomerStatusEnum.class, message = "客户状态无效")
    private String status;
}
