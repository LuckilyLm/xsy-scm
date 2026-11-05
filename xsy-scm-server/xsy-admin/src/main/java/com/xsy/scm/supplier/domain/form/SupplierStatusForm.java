package com.xsy.scm.supplier.domain.form;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.common.validation.ScmEnumValue;

/**
 * 供应商状态变更命令。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SupplierStatusForm extends SupplierDeleteForm {

    @NotNull(message = "供应商状态不能为空")
    @ScmEnumValue(enumClass = ScmEnableStatusEnum.class, message = "供应商状态无效")
    private String status;
}
