package com.xsy.scm.customer.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.common.constant.ScmEnableStatusEnum;

/**
 * 新增客户类型。
 *
 * <p>状态随表单带入、没有独立的状态端点：客户类型是低频字典数据，
 * 单独开一个状态端点没有收益。
 */
@Data
public class CustomerTypeAddForm {

    @NotBlank(message = "客户类型编码不能为空")
    @Size(max = 64, message = "客户类型编码不能超过64个字符")
    private String typeCode;

    @NotBlank(message = "客户类型名称不能为空")
    @Size(max = 100, message = "客户类型名称不能超过100个字符")
    private String name;

    @NotNull(message = "客户类型状态不能为空")
    @ScmEnumValue(enumClass = ScmEnableStatusEnum.class, message = "客户类型状态无效")
    private String status = ScmEnableStatusEnum.ENABLED.name();
}
